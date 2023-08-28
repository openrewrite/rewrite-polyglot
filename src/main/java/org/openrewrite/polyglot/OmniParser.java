/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.polyglot;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.json.JsonParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.protobuf.ProtoParser;
import org.openrewrite.shaded.jgit.ignore.IgnoreNode;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OmniParser implements Parser {
    /**
     * Does not include text and quark parsers. We leave it up to the caller to determine
     * what the division of labor should be between PlainText and the Quark parsers, if any.
     */
    public static List<Parser> RESOURCE_PARSERS = new ArrayList<>(asList(
            new JsonParser(),
            new XmlParser(),
            new YamlParser(),
            new PropertiesParser(),
            new ProtoParser(),
            HclParser.builder().build()
    ));

    private static final Collection<String> DEFAULT_IGNORED_DIRECTORIES = asList(
            "build",
            "target",
            "out",
            ".gradle",
            ".idea",
            ".project",
            "node_modules",
            ".git",
            ".metadata",
            ".DS_Store",
            ".moderne"
    );

    private final Collection<Path> exclusions;
    private final Collection<PathMatcher> exclusionMatchers;
    private final int sizeThresholdMb;
    private final Collection<Path> excludedDirectories;
    private final boolean parallel;
    private final List<Parser> parsers;
    private final Consumer<Integer> onParse;

    public Stream<SourceFile> parseAll(Path rootDir) {
        return parse(acceptedPaths(rootDir), rootDir, new InMemoryExecutionContext());
    }

    @Override
    public Stream<SourceFile> parse(Iterable<Path> sourceFiles, @Nullable Path relativeTo, ExecutionContext ctx) {
        int count = 0;
        for (Path ignored : sourceFiles) {
            count++;
        }
        onParse.accept(count);
        return Parser.super.parse(sourceFiles, relativeTo, ctx);
    }

    public List<Path> acceptedPaths(Path rootDir) {
        List<Path> parseable = new ArrayList<>();
        Map<Path, IgnoreNode> gitignoreStack = new LinkedHashMap<>();

        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    loadGitignore(dir).ifPresent(ignoreNode -> gitignoreStack.put(dir, ignoreNode));
                    return isExcluded(dir, rootDir) ||
                           isIgnoredDirectory(dir, rootDir) ||
                           excludedDirectories.contains(dir) ||
                           isGitignored(gitignoreStack.values(), dir, rootDir) ?
                            FileVisitResult.SKIP_SUBTREE :
                            FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isOther() && !attrs.isSymbolicLink() &&
                        !isExcluded(file, rootDir) &&
                        !isGitignored(gitignoreStack.values(), file, rootDir)) {
                        if (!isOverSizeThreshold(attrs.size())) {
                            for (Parser parser : RESOURCE_PARSERS) {
                                if (parser.accept(file)) {
                                    parseable.add(file);
                                    break;
                                }
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    gitignoreStack.remove(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (
                IOException e) {
            // cannot happen, since none of the visit methods throw an IOException
            throw new UncheckedIOException(e);
        }
        return parseable;
    }

    @Override
    public Stream<SourceFile> parseInputs(Iterable<Input> sources, @Nullable Path relativeTo,
                                          ExecutionContext ctx) {
        return StreamSupport.stream(sources.spliterator(), parallel).flatMap(input -> {
            Path path = input.getPath();
            for (Parser parser : RESOURCE_PARSERS) {
                if (parser.accept(path)) {
                    return parser.parseInputs(Collections.singletonList(input), relativeTo, ctx);
                }
            }
            return Stream.empty();
        });
    }

    @Override
    public boolean accept(Path path) {
        for (Parser parser : RESOURCE_PARSERS) {
            if (parser.accept(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Path sourcePathFromSourceText(Path prefix, String sourceCode) {
        return Paths.get("resource.me");
    }

    private boolean isOverSizeThreshold(long fileSize) {
        return sizeThresholdMb > 0 && fileSize > sizeThresholdMb * 1024L * 1024L;
    }

    boolean isExcluded(Path path, Path rootDir) {
        Path relativePath = rootDir.relativize(path);
        if (exclusions.contains(relativePath)) {
            return true;
        }
        for (PathMatcher excluded : exclusionMatchers) {
            if (excluded.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIgnoredDirectory(Path path, Path rootDir) {
        for (Path pathSegment : rootDir.relativize(path)) {
            if (DEFAULT_IGNORED_DIRECTORIES.contains(pathSegment.toString())) {
                return true;
            }
        }
        return false;
    }

    private Optional<IgnoreNode> loadGitignore(Path dir) {
        Path gitignore = dir.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            return Optional.empty();
        }
        IgnoreNode ignoreNode = new IgnoreNode();
        try (InputStream is = Files.newInputStream(gitignore)) {
            ignoreNode.parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading '" + gitignore + "'", e);
        }
        return Optional.of(ignoreNode);
    }

    private boolean isGitignored(Collection<IgnoreNode> gitignoreStack, Path path, Path rootDir) {
        // We are retrieving the elements in insertion order thanks to Deque
        for (IgnoreNode ignoreNode : gitignoreStack) {
            Boolean result = ignoreNode.checkIgnored(rootDir.relativize(path).toFile().getPath(), path.toFile().isDirectory());
            if (result != null) {
                return result;
            }
        }
        return false;
    }

    public static Builder builder(Parser... parsers) {
        return builder(asList(parsers));
    }

    public static Builder builder(List<Parser> parsers, Parser... more) {
        if (more.length > 0) {
            List<Parser> all = new ArrayList<>(parsers);
            all.addAll(asList(more));
            parsers = all;
        }
        return new Builder(parsers);
    }

    public static class Builder extends Parser.Builder {
        private Collection<Path> exclusions = emptyList();
        private Collection<PathMatcher> exclusionMatchers = emptyList();
        private int sizeThresholdMb = 10;
        private Collection<Path> excludedDirectories = emptyList();
        private boolean parallel;
        private Consumer<Integer> onParse = inputCount -> {
        };
        private final List<Parser> parsers;

        public Builder(List<Parser> parsers) {
            super(SourceFile.class);
            this.parsers = parsers;
        }

        public Builder exclusions(Collection<Path> exclusions) {
            this.exclusions = exclusions;
            return this;
        }

        public Builder exclusionMatchers(Collection<PathMatcher> exclusions) {
            this.exclusionMatchers = exclusions;
            return this;
        }

        public Builder exclusionMatchers(Path basePath, Iterable<String> exclusions) {
            return exclusionMatchers(StreamSupport.stream(exclusions.spliterator(), false)
                    .map((o) -> basePath.getFileSystem().getPathMatcher("glob:" + o))
                    .collect(Collectors.toList()));
        }

        public Builder sizeThresholdMb(int sizeThresholdMb) {
            this.sizeThresholdMb = sizeThresholdMb;
            return this;
        }

        public Builder excludedDirectories(Collection<Path> excludedDirectories) {
            this.excludedDirectories = excludedDirectories;
            return this;
        }

        public Builder onParse(Consumer<Integer> onParse) {
            this.onParse = onParse;
            return this;
        }

        /**
         * Resource parsers are safe to execute in parallel. This is not true of all parsers, for example
         * the MavenParser.
         *
         * @param parallel whether the parser stream should be parallelized.
         * @return this builder.
         */
        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        @Override
        public OmniParser build() {
            return new OmniParser(exclusions, exclusionMatchers, sizeThresholdMb,
                    excludedDirectories, parallel, parsers, onParse);
        }

        @Override
        public String getDslName() {
            return "omni";
        }
    }
}
