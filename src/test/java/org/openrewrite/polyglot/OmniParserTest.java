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
import org.openrewrite.shaded.jgit.api.Git;
import org.openrewrite.shaded.jgit.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OmniParserTest {

    @Test
    void isExcluded() {
        OmniParser parser = OmniParser.builder(OmniParser.defaultResourceParsers())
          .exclusions(List.of(Paths.get("pom.xml")))
          .build();
        assertThat(parser.isExcluded(Paths.get("/Users/jon/Projects/github/quarkusio/gizmo/pom.xml"),
          Paths.get("/Users/jon/Projects/github/quarkusio/gizmo"))).isTrue();
    }

    @Test
    void acceptedPaths(@TempDir Path repo) throws IOException {
        // Prepare repo
        FileUtils.touch(repo.resolve("pom.xml"));
        FileUtils.touch(repo.resolve("gitignored.xml"));
        FileUtils.touch(repo.resolve("localexclude.xml"));
        Files.writeString(repo.resolve(".gitignore"), "gitignored.xml");
        FileUtils.touch(repo.resolve("file.xml"));
        FileUtils.mkdirs(repo.resolve("folder").toFile());
        FileUtils.touch(repo.resolve("folder/fileinfolder.xml"));
        initGit(repo);
        FileUtils.mkdirs(repo.resolve(".git/info").toFile());
        Files.writeString(repo.resolve(".git/info/exclude"), "localexclude.xml");
        FileUtils.touch(repo.resolve("newfile.xml"));
        FileUtils.createSymLink(repo.resolve("symlink.xml").toFile(), "./newfile.xml");
        // Repo prepared

        OmniParser parser = OmniParser.builder(OmniParser.defaultResourceParsers())
          .exclusions(List.of(Paths.get("pom.xml")))
          .build();
        List<Path> paths = parser.acceptedPaths(repo);
        assertThat(paths).contains(
          repo.resolve("file.xml"),
          repo.resolve("newfile.xml"),
          repo.resolve("folder/fileinfolder.xml")
        ).doesNotContain(
          repo.resolve("pom.xml"),
          repo.resolve("gitignored.xml"),
          repo.resolve("localexclude.xml"),
          repo.resolve("symlink.xml")
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
