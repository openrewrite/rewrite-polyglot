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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.polyglot.RemoteProgressMessage.Type.*;

class RemoteProgressMessageTest {

    @Test
    void multipartExtraMessage() {
        String itsALongStory = "this is some pretty long text that will exceed the " +
                               "maximum single-packet message length and will therefore require " +
                               "splitting into multiple packets which may arrive in any order or" +
                               "even not at all potentially";

        List<byte[]> packets = RemoteProgressMessage.toPackets(
          SetExtraMessage, itsALongStory);

        // randomize packet order
        packets.sort(Comparator.comparing(packet -> packet[45]));

        Map<UUID, RemoteProgressMessage> incompleteMessages = new HashMap<>();
        RemoteProgressMessage message = null;
        for (byte[] packet : packets) {
            message = RemoteProgressMessage.read(packet, packet.length, incompleteMessages);
        }

        assertThat(message).isNotNull();
        assertThat(incompleteMessages).isEmpty();
        assertThat(message.getType()).isEqualTo(SetExtraMessage);
        assertThat(message.getMessage()).isEqualTo(itsALongStory);
    }

    @Test
    void max() {
        String max = "100";
        List<byte[]> packets = RemoteProgressMessage.toPackets(SetMax, max);

        // randomize packet order
        packets.sort(Comparator.comparing(packet -> packet[43]));

        Map<UUID, RemoteProgressMessage> incompleteMessages = new HashMap<>();
        RemoteProgressMessage message = null;
        for (byte[] packet : packets) {
            message = RemoteProgressMessage.read(packet, packet.length, incompleteMessages);
        }

        assertThat(message).isNotNull();
        assertThat(incompleteMessages).isEmpty();
        assertThat(message.getType()).isEqualTo(SetMax);
        assertThat(message.getMessage()).isEqualTo(max);
    }

    @Test
    void step() {
        List<byte[]> packets = RemoteProgressMessage.toPackets(Step, null);

        // randomize packet order
        packets.sort(Comparator.comparing(packet -> packet[45]));

        Map<UUID, RemoteProgressMessage> incompleteMessages = new HashMap<>();
        RemoteProgressMessage message = null;
        for (byte[] packet : packets) {
            message = RemoteProgressMessage.read(packet, packet.length, incompleteMessages);
        }

        assertThat(message).isNotNull();
        assertThat(incompleteMessages).isEmpty();
        assertThat(message.getType()).isEqualTo(Step);
        assertThat(message.getMessage()).isNull();
    }
}
