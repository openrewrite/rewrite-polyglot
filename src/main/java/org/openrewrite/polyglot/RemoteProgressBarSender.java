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

import org.jspecify.annotations.Nullable;
import org.openrewrite.polyglot.RemoteProgressMessage.Type;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RemoteProgressBarSender implements ProgressBar {
    private DatagramSocket socket;
    private InetAddress address;
    private int port;

    public RemoteProgressBarSender(int port) {
        this(null, port);
    }

    public RemoteProgressBarSender(@Nullable InetAddress address, int port) {
        String localhost = Files.exists(Paths.get("/.dockerenv")) ?
                "host.docker.internal" :
                "localhost";
        try {
            this.socket = new DatagramSocket();
            this.port = port;
            this.address = address == null ? InetAddress.getByName(localhost) : address;
        } catch (UnknownHostException | SocketException e) {
            if ("host.docker.internal".equals(localhost)) {
                try {
                    this.address = InetAddress.getByName("localhost");
                    this.port = port;
                    this.socket = new DatagramSocket();
                } catch (UnknownHostException | SocketException ex) {
                    throw new UncheckedIOException(ex);
                }
            } else {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void intermediateResult(@Nullable String message) {
        send(Type.IntermediateResult, message);
    }

    @Override
    public void finish(String message) {
        throw new UnsupportedOperationException("The finish message must be determined by the receiver");
    }

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public void step() {
        send(Type.Step, null);
    }

    @Override
    public ProgressBar setExtraMessage(String extraMessage) {
        send(Type.SetExtraMessage, extraMessage);
        return this;
    }

    @Override
    public ProgressBar setMax(int max) {
        send(Type.SetMax, Integer.toString(max));
        return this;
    }

    public void throwRemote(RemoteException ex) {
        send(Type.Exception, ex.encode());
    }

    private void send(Type type, @Nullable String message) {
        try {
            for (byte[] packet : RemoteProgressMessage.toPackets(type, message)) {
                socket.send(new DatagramPacket(packet, packet.length, address, port));
            }
        } catch (SocketException ignored) {
            // the remote receiver may not be listening any longer, so ignore
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
