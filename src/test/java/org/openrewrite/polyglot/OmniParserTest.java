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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.jgit.api.Git;
import org.openrewrite.jgit.transport.URIish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.PathUtils.separatorsToSystem;
import static org.openrewrite.jgit.util.FileUtils.*;

class OmniParserTest {

    @Test
    void isExcluded(@TempDir Path root) {
        OmniParser parser = OmniParser.builder(OmniParser.defaultResourceParsers())
          .exclusions(List.of(Paths.get("pom.xml")))
          .build();
        assertThat(parser.isExcluded(root.resolve("pom.xml"), root)).isTrue();
    }

    @TempDir
    Path repo;

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void acceptedPaths(boolean gitRepo) throws IOException {
        touch(repo.resolve("file.xml"));
        mkdirs(repo.resolve("folder").toFile());
        touch(repo.resolve("folder/fileinfolder.xml"));
        touch(repo.resolve("newfile.xml"));
        mkdirs(repo.resolve(".gradle").toFile());
        touch(repo.resolve(".gradle/foo.yml"));

        touch(repo.resolve("pom.xml"));
        createSymLink(repo.resolve("symlink.xml").toFile(), "./newfile.xml");

        mkdirs(repo.resolve("build").toFile());
        touch(repo.resolve("ignored_directory_file.xml"));

        if (gitRepo) {
            initGit(repo);

            touch(repo.resolve("gitignored.xml"));
            touch(repo.resolve("localexclude.xml"));
            writeString(repo.resolve(".gitignore"), "gitignored.xml");
            mkdirs(repo.resolve(".git/info").toFile());
            writeString(repo.resolve(".git/info/exclude"), "localexclude.xml");
        }

        OmniParser parser = OmniParser.builder(OmniParser.defaultResourceParsers())
          .exclusions(List.of(Paths.get("pom.xml")))
          .build();

        List<Path> paths = parser.acceptedPaths(repo);
        assertThat(paths.stream().map(p -> repo.relativize(p).toString())).contains(
          "file.xml",
          "newfile.xml",
          separatorsToSystem("folder/fileinfolder.xml")
        ).doesNotContain(
          "pom.xml",
          "symlink.xml",
          separatorsToSystem("build/ignored_directory_file.xml"),
          "gitignored.xml",
          "localexclude.xml",
          separatorsToSystem(".gradle/foo.yml")
        );

        assertThat(parser.acceptedPaths(repo, repo.resolve("folder")))
          .containsExactlyInAnyOrder(repo.resolve("folder/fileinfolder.xml"));
    }

    /**
     * For some parsers it is acceptable to parse sources independently, e.g.: xml.
     * This isn't the case for Java and similar languages where related sources need to be parsed together
     * so that references to types defined in other sources can be understood and type-attributed.
     */
    @Test
    void javaSourcesNotSensitiveToOrder() throws IOException {
        Path superPath = repo.resolve("Super.java");
        Files.write(superPath, "public class Super {}".getBytes());
        Path basePath = repo.resolve("Base.java");
        Files.write(basePath, "public class Base extends Super {}".getBytes());

        List<SourceFile> parsed = OmniParser.builder(JavaParser.fromJavaVersion().build())
          .build()
          .parse(List.of(basePath, superPath), repo, new InMemoryExecutionContext())
          .toList();

        assertThat(parsed).hasSize(2);
        assertThat(parsed.stream()
          .filter(it -> it.getSourcePath().toString().contains("Base"))
          .map(J.CompilationUnit.class::cast)
          .findAny())
          .isPresent()
          .get()
          .as("Type attribution for class \"Base\" class should include that its supertype is \"Super\"")
          .matches(base -> {
              JavaType.FullyQualified baseType = base.getClasses().get(0).getType();
              if (baseType instanceof JavaType.Class) {
                  return TypeUtils.isWellFormedType(baseType.getSupertype());
              }
              return false;
          });
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void buildRootDiffersFromRepositoryRoot(boolean gitRepo) throws IOException {
        if(gitRepo) {
            initGit(repo);
        }
        Path projectRoot = repo.resolve("project");
        mkdirs(projectRoot.resolve(".gradle").toFile());
        touch(projectRoot.resolve(".gradle/foo.yml"));
        touch(projectRoot.resolve("build.gradle"));
        List<Path> paths = OmniParser.builder(OmniParser.defaultResourceParsers())
          .build()
          .acceptedPaths(repo, projectRoot).stream()
          .map(repo::relativize)
          .toList();

        assertThat(paths)
          .contains(Paths.get("project/build.gradle"))
          .doesNotContain(Paths.get("project/.gradle/foo.yml"));
    }

    void initGit(Path repositoryPath) {
        try (Git git = Git.init().setDirectory(repositoryPath.toFile()).call()) {
            git.remoteSetUrl().setRemoteName("origin").setRemoteUri(
                new URIish().setHost("git@github.com").setPort(80)
                  .setPath("acme/" + repositoryPath.getFileName() + ".git"))
              .call();
            git.add().addFilepattern(".").call();
            git.commit().setSign(false).setMessage("init commit").call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
