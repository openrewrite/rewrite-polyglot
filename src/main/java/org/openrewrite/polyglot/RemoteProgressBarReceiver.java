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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class RemoteProgressBarReceiver implements ProgressBar {
    private static final ExecutorService PROGRESS_RECEIVER_POOL = Executors.newCachedThreadPool();

    private final ProgressBar delegate;
    private final DatagramSocket socket;
    private volatile boolean closed;
    private final AtomicReference<String> thrown = new AtomicReference<>();

    public RemoteProgressBarReceiver(ProgressBar delegate) {
        try {
            this.delegate = delegate;
            this.socket = new DatagramSocket();
            PROGRESS_RECEIVER_POOL.submit(this::receive);
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public int receive() {
        Map<UUID, RemoteProgressMessage> incompleteMessages = new LinkedHashMap<UUID, RemoteProgressMessage>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, RemoteProgressMessage> eldest) {
                return size() > 1000;
            }
        };
        try {
            while (!closed) {
                RemoteProgressMessage message = RemoteProgressMessage.receive(socket, incompleteMessages);
                if (message == null) {
                    continue;
                }
                switch (message.getType()) {
                    case Exception:
                        if (message.getMessage() != null) {
                            thrown.set(message.getMessage());
                        }
                        break;
                    case IntermediateResult:
                        delegate.intermediateResult(message.getMessage());
                        break;
                    case Step:
                        delegate.step();
                        break;
                    case SetExtraMessage:
                        delegate.setExtraMessage(requireNonNull(message.getMessage()));
                        break;
                    case SetMax:
                        delegate.setMax(Integer.parseInt(requireNonNull(message.getMessage())));
                        break;
                }
            }
        } catch (IOException e) {
            if (!closed) {
                throw new UncheckedIOException(e);
            }
        }
        return 0;
    }

    @Override
    public void intermediateResult(@Nullable String message) {
        maybeThrow();
        delegate.intermediateResult(message);
    }

    @Override
    public void finish(String message) {
        maybeThrow();
        delegate.finish(message);
    }

    @Override
    public void step() {
        maybeThrow();
        delegate.step();
    }

    @Override
    public ProgressBar setExtraMessage(String extraMessage) {
        maybeThrow();
        return delegate.setExtraMessage(extraMessage);
    }

    @Override
    public ProgressBar setMax(int max) {
        maybeThrow();
        return delegate.setMax(max);
    }

    @Override
    public void close() {
        closed = true;
        socket.close();
        maybeThrow();
    }

    private void maybeThrow() {
        String t = thrown.get();
        if (t != null) {
            throw RemoteException.decode(t);
        }
    }
}
