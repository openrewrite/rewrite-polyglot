package org.openrewrite.polyglot;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OmniParserTest {

    @Test
    void isExcluded() {
        OmniParser parser = OmniParser.builder()
          .exclusions(List.of(Paths.get("pom.xml")))
          .build();
        assertThat(parser.isExcluded(Paths.get("/Users/jon/Projects/github/quarkusio/gizmo/pom.xml"),
          Paths.get("/Users/jon/Projects/github/quarkusio/gizmo"))).isTrue();
    }
}
