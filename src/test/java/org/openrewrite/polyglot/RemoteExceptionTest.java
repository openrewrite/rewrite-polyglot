/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.polyglot;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteExceptionTest {

    @Test
    void encodeDecode() {
        RemoteException remote = RemoteException.builder("This is a bad thing")
          .cause(new RuntimeException("boom"), "org.openrewrite")
          .fixSuggestions("Please fix")
          .partialSuccess(true)
          .build();

        RemoteException decoded = RemoteException.decode(remote.encode());
        assertThat(decoded).isEqualTo(remote);
    }

    @Test
    void encodeDecodeWithoutCause() {
        RemoteException remote = RemoteException.builder("This is a bad thing").fixSuggestions("Please fix").build();

        RemoteException decoded = RemoteException.decode(remote.encode());
        assertThat(decoded).isEqualTo(remote);
    }

    @Test
    void encodeDecodeWithoutSuggestions() {
        RemoteException remote = RemoteException.builder("This is a bad thing")
          .cause(new RuntimeException("boom"), "org.openrewrite")
          .build();

        RemoteException decoded = RemoteException.decode(remote.encode());
        assertThat(decoded).isEqualTo(remote);
    }

    @Test
    void decodeWithoutPartialSuccess() {
        // To test deserializing exceptions serialized using legacy versions of rewrite-polyglot
        Base64.Encoder base64 = Base64.getEncoder();
        StringBuilder builder = new StringBuilder(256);
        builder.append(base64.encodeToString("This is a bad thing".getBytes(StandardCharsets.UTF_8))).append('\n');
        builder.append("null").append('\n');
        builder.append("null");

        RemoteException remote = RemoteException.builder("This is a bad thing").build();

        RemoteException decoded = RemoteException.decode(builder.toString());
        assertThat(decoded.getMessage()).isEqualTo(remote.getMessage());
        assertThat(decoded.getCause()).isEqualTo(remote.getCause());
        assertThat(decoded.getFixSuggestions()).isEqualTo(remote.getFixSuggestions());
        assertThat(decoded.isPartialSuccess()).isEqualTo(remote.isPartialSuccess());
    }

    @Test
    void printStackTrace() {
        RemoteException remote = RemoteException.builder("This is a bad thing")
          .cause(new RuntimeException("boom"))
          .fixSuggestions("Please fix")
          .partialSuccess(true)
          .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter p = new PrintWriter(out)) {
            remote.printStackTrace(p);
        }

        String stackTrace = out.toString();
        assertThat(stackTrace).contains("org.openrewrite.polyglot.RemoteException: This is a bad thing");
        assertThat(stackTrace).contains("java.lang.RuntimeException: boom");
        assertThat(stackTrace).contains("org.openrewrite.polyglot.RemoteExceptionTest.printStackTrace");
    }
}
