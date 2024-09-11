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

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
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
        RemoteException remote = RemoteException.builder("This is a bad thing")
          .fixSuggestions("Please fix")
          .build();

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
        builder.append(base64.encodeToString("This is a bad thing".getBytes(UTF_8))).append('\n');
        builder.append("null").append('\n');
        builder.append("null");

        RemoteException remote = RemoteException.builder("This is a bad thing")
          .build();

        RemoteException decoded = RemoteException.decode(builder.toString());
        assertThat(decoded.getMessage()).isEqualTo(remote.getMessage());
        assertThat(decoded.getCause()).isEqualTo(remote.getCause());
        assertThat(decoded.getFixSuggestions()).isEqualTo(remote.getFixSuggestions());
        assertThat(decoded.isPartialSuccess()).isEqualTo(remote.isPartialSuccess());
    }
}
