/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
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
