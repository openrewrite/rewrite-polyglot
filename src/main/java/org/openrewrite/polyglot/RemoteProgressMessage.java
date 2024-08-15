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

import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

class RemoteProgressMessage {
    private static final String EOM = "__EOM__";
    private static final int PACKET_LENGTH = 128;

    @Getter
    private final UUID id;

    @Getter
    private Type type;

    private final Map<Integer, String> fragments = new TreeMap<>();
    private int fragmentTotal = Integer.MAX_VALUE;

    RemoteProgressMessage(UUID id) {
        this.id = id;
    }

    public @Nullable String getMessage() {
        return fragments.isEmpty() ? null : String.join("", fragments.values());
    }

    enum Type {
        IntermediateResult,
        Step,
        SetExtraMessage,
        SetMax,
        Exception
    }

    public static @Nullable RemoteProgressMessage receive(DatagramSocket socket, Map<UUID, RemoteProgressMessage> incompleteMessages)
      throws IOException {
        byte[] buf = new byte[PACKET_LENGTH];
        DatagramPacket packet = new DatagramPacket(buf, PACKET_LENGTH);
        try {
            socket.receive(packet);
            return read(buf, packet.getLength(), incompleteMessages);
        } catch (SocketTimeoutException ignored) {
        }
        return null;
    }

    /**
     * @param packet             The packet just received
     * @param length             The length of the packet contents, which may be shorter than the
     *                           packet byte array length.
     * @param incompleteMessages A collection of incomplete messages.
     * @return A {@link RemoteProgressMessage} if the message is completed by this packet, null otherwise.
     */
    public static @Nullable RemoteProgressMessage read(byte[] packet, int length, Map<UUID, RemoteProgressMessage> incompleteMessages) {
        if (length < 42) {
            return null; // not a V2 packet;
        }
        byte[] preambleBytes = new byte[42];
        System.arraycopy(packet, 0, preambleBytes, 0, 42);
        String preamble = new String(preambleBytes);

        if (!preamble.startsWith("v2")) {
            return null;
        }

        UUID messageId = UUID.fromString(preamble.substring(2, 38));
        RemoteProgressMessage message = incompleteMessages.computeIfAbsent(messageId, RemoteProgressMessage::new);

        int typeOrdinal = Integer.parseInt(preamble.substring(38, 39));
        for (Type t : Type.values()) {
            if (typeOrdinal == t.ordinal()) {
                message.type = t;
                break;
            }
        }

        int index = Integer.parseInt(preamble.substring(39, 42));

        byte[] messageFragmentBytes = new byte[length - 42];
        System.arraycopy(packet, 42, messageFragmentBytes, 0, length - 42);
        String messageFragment = new String(messageFragmentBytes);
        if (messageFragment.equals(EOM)) {
            message.fragmentTotal = index; // index is zero-based
        } else {
            message.fragments.put(index, messageFragment);
        }

        if (message.fragments.size() == message.fragmentTotal) {
            incompleteMessages.remove(messageId);
            return message;
        }
        return null;
    }

    public static List<byte[]> toPackets(Type type, @Nullable String message) {
        String messageId = UUID.randomUUID().toString();
        int index = 0;
        List<byte[]> packets = new ArrayList<>();

        if (message != null) {
            byte[] messageBytes = message.getBytes();
            for (int i = 0; i < messageBytes.length; index++) {
                byte[] preamble = ("v2" + messageId + type.ordinal() + encodeIndex(index)).getBytes(UTF_8);
                int packetMessageLength = Math.min(PACKET_LENGTH - preamble.length,
                        Math.min(messageBytes.length - i, PACKET_LENGTH));

                byte[] packetBytes = new byte[packetMessageLength + preamble.length];
                System.arraycopy(preamble, 0, packetBytes, 0, preamble.length);
                System.arraycopy(messageBytes, i, packetBytes, preamble.length, packetMessageLength);

                i += packetMessageLength;
                packets.add(packetBytes);
            }
        }

        packets.add(("v2" + messageId + type.ordinal() + encodeIndex(index) + EOM).getBytes(UTF_8));
        return packets;
    }

    private static String encodeIndex(int index) {
        String indexStr = Integer.toString(index);
        if (index < 10) {
            indexStr = "0" + indexStr;
        }
        if (index < 100) {
            indexStr = "0" + indexStr;
        }
        return indexStr;
    }
}
