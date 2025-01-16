/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.SourceFile;
import org.openrewrite.text.PlainTextParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFileStreamTest {

    @Test
    void singleProject() {
        Stream<SourceFile> p1Sources = parse("a", "b", "c");
        Stream<SourceFile> p1Resources = parse("d", "e", "f");
        List<String> groups = new ArrayList<>();

        assertThat(SourceFileStream.build("p1", groups::add)
          .concat(p1Sources, 3)
          .concat(p1Resources, 3)
          .reduce(0, (n, sourceFile) -> n + 1, Integer::sum)).isEqualTo(6);
        assertThat(groups).containsExactly("p1");
    }

    @Test
    void multipleProjects() {
        Stream<SourceFile> p1Sources = parse("a", "b", "c");
        Stream<SourceFile> p2Sources = parse("d", "e", "f");
        List<String> groups = new ArrayList<>();

        assertThat(
          Stream.concat(
              SourceFileStream.build("p1", groups::add).concat(p1Sources, 3),
              SourceFileStream.build("p2", groups::add).concat(p2Sources, 3)
            )
            .reduce(0, (n, sourceFile) -> n + 1, Integer::sum)).isEqualTo(6);
        assertThat(groups).containsExactly("p1", "p2");
    }

    private static Stream<SourceFile> parse(String... files) {
        return PlainTextParser.builder().build().parse(files);
    }
}
