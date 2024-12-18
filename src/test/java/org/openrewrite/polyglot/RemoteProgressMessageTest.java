/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
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
