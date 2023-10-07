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

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteExceptionTest {
    RemoteException remote = RemoteException.builder("This is a bad thing")
      .cause(new RuntimeException("boom"), "org.openrewrite")
      .fixSuggestions("Please fix")
      .build();

    @Test
    void encodeDecode() {
        RemoteException decoded = RemoteException.decode(remote.encode());
        assertThat(decoded).isEqualTo(remote);
    }

    @Test
    void sendOverProgressBar() {
        try (RemoteProgressBarReceiver progressBar = new RemoteProgressBarReceiver(new NoopProgressBar());
             RemoteProgressBarSender remoteProgressBar = new RemoteProgressBarSender(progressBar.getPort())) {
            remoteProgressBar.throwRemote(remote);
        }
    }
}
