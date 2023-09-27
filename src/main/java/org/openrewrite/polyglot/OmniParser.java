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
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.json.JsonParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.protobuf.ProtoParser;
import org.openrewrite.shaded.jgit.api.Git;
import org.openrewrite.shaded.jgit.lib.FileMode;
import org.openrewrite.shaded.jgit.treewalk.FileTreeIterator;
import org.openrewrite.shaded.jgit.treewalk.TreeWalk;
import org.openrewrite.shaded.jgit.treewalk.WorkingTreeIterator;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

@SuppressWarnings("unused")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OmniParser implements Parser {
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

    /**
     * Does not include text and quark parsers. We leave it up to the caller to determine
     * what the division of labor should be between PlainText and the Quark parsers, if any.
     */
    public static List<Parser> defaultResourceParsers() {
        // do not assign to static field, or class initialization on OmniParser will
        // cause these parsers to get built, potentially triggering undesirable side effects
        return new ArrayList<>(asList(
                new JsonParser(),
                new XmlParser(),
                new YamlParser(),
                new PropertiesParser(),
                new ProtoParser(),
                HclParser.builder().build(),
                GroovyParser.builder().build(),
                GradleParser.builder().build()
        ));
    }

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
        return acceptedPaths(rootDir, rootDir);
    }

    public List<Path> acceptedPaths(Path rootDir, Path searchDir) {
        if (!Files.exists(searchDir)) {
            return emptyList();
        }
        List<Path> parseable = new ArrayList<>();

        try (Git git = Git.open(rootDir.toFile());
             TreeWalk walk = new TreeWalk(git.getRepository())) {
            walk.addTree(new FileTreeIterator(git.getRepository()));

            while (walk.next()) {
                WorkingTreeIterator tree = walk.getTree(WorkingTreeIterator.class);
                if (!tree.isEntryIgnored()) {
                    Path path = rootDir.resolve(tree.getEntryPathString());
                    if (tree.getEntryFileMode().equals(FileMode.TREE)) {
                        // It's a directory
                        if (!isExcluded(path, rootDir) &&
                            !isIgnoredDirectory(path, searchDir) &&
                            !excludedDirectories.contains(path)
                        ) {
                            walk.enterSubtree();
                        }
                    } else {
                        // It's a file
                        if (!tree.getEntryFileMode().equals(FileMode.SYMLINK) &&
                            !isOverSizeThreshold(tree.getEntryContentLength()) &&
                            !isExcluded(path, rootDir)
                        ) {
                            for (Parser parser : parsers) {
                                if (parser.accept(path)) {
                                    parseable.add(path);
                                    break;
                                }
                            }
                        }
                    }

                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parseable;
    }

    @Override
    public Stream<SourceFile> parseInputs(Iterable<Input> sources, @Nullable Path relativeTo,
                                          ExecutionContext ctx) {
        return StreamSupport.stream(sources.spliterator(), parallel).flatMap(input -> {
            Path path = input.getPath();
            for (Parser parser : parsers) {
                if (parser.accept(path)) {
                    return parser.parseInputs(Collections.singletonList(input), relativeTo, ctx);
                }
            }
            return Stream.empty();
        });
    }

    @Override
    public boolean accept(Path path) {
        for (Parser parser : parsers) {
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
