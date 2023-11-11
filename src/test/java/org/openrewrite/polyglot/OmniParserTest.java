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
import org.openrewrite.shaded.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.PathUtils.separatorsToSystem;
import static org.openrewrite.shaded.jgit.util.FileUtils.*;

public class OmniParserTest {

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
          "localexclude.xml"
        );
    }

    void initGit(Path repositoryPath) {
        try (Git git = Git.init().setDirectory(repositoryPath.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setSign(false).setMessage("init commit").call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
