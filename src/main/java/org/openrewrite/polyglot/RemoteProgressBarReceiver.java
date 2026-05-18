/*
 * Copyright 2025 the original author or authors.
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
import java.net.*;
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
    private final AtomicReference<@Nullable String> thrown = new AtomicReference<>();
    private volatile boolean canceled = false;
    private @Nullable InetAddress lastSenderAddress;
    private int lastSenderPort;
    private volatile boolean cancelNotificationSent = false;

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
                // Receive with packet info to get sender details
                byte[] buf = new byte[128];
                DatagramPacket packet = new DatagramPacket(buf, 128);
                try {
                    socket.receive(packet);

                    // Store sender info for sending cancel status back
                    lastSenderAddress = packet.getAddress();
                    lastSenderPort = packet.getPort();

                    RemoteProgressMessage message = RemoteProgressMessage.read(buf, packet.getLength(), incompleteMessages);
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

                    // Only send cancel status if we haven't already notified about cancellation
                    if ((canceled || delegate.isCanceled()) && !cancelNotificationSent) {
                        sendCancelStatus();
                        cancelNotificationSent = true;
                    }
                } catch (SocketTimeoutException ignored) {
                    // No message received, continue
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

    private void sendCancelStatus() {
        if (lastSenderAddress != null && lastSenderPort > 0) {
            try {
                // Send a cancel notification message
                String cancelMessage = "CANCEL:true";
                byte[] cancelBytes = cancelMessage.getBytes();
                DatagramPacket cancelPacket = new DatagramPacket(
                    cancelBytes,
                    cancelBytes.length,
                    lastSenderAddress,
                    lastSenderPort
                );

                // Try a few times to ensure delivery (since we only send once per cancellation)
                for (int i = 0; i < 3; i++) {
                    socket.send(cancelPacket);
                    if (i < 2) {
                        Thread.sleep(10); // Small delay between retries
                    }
                }
            } catch (IOException | InterruptedException ignored) {
                // Ignore failures when sending cancel status
            }
        }
    }

    @Override
    public void setCanceled(boolean canceled) {
        boolean wasNotCanceled = !this.canceled;
        this.canceled = canceled;
        delegate.setCanceled(canceled);

        // If we just became canceled and haven't sent notification yet, send it
        if (wasNotCanceled && canceled && !cancelNotificationSent) {
            sendCancelStatus();
            cancelNotificationSent = true;
        }
    }

    @Override
    public boolean isCanceled() {
        return canceled || delegate.isCanceled();
    }
}
