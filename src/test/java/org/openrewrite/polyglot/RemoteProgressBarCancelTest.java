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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.DatagramPacket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class RemoteProgressBarCancelTest {

    @Test
    @Timeout(5)
    public void testCancelPropagation() throws InterruptedException {
        // Create a mock delegate progress bar
        TestProgressBar delegate = new TestProgressBar();

        // Create receiver
        RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(delegate);
        int port = receiver.getPort();

        // Create sender connected to receiver
        RemoteProgressBarSender sender = new RemoteProgressBarSender(port);

        try {
            // Initially neither should be canceled
            assertFalse(sender.isCanceled());
            assertFalse(receiver.isCanceled());
            assertFalse(delegate.isCanceled());

            // Send a progress message to establish communication
            sender.step();
            Thread.sleep(100); // Give time for message to be received

            // Now cancel the delegate
            delegate.setCanceled(true);
            assertTrue(delegate.isCanceled());
            assertTrue(receiver.isCanceled()); // Receiver should reflect delegate state

            // Send another message - this should trigger cancel status to be sent back
            sender.step();
            Thread.sleep(100); // Give time for cancel status to propagate back

            // Sender should now be canceled
            assertTrue(sender.isCanceled());

        } finally {
            sender.close();
            receiver.close();
        }
    }

    @Test
    @Timeout(5)
    public void testCancelPropagationFromReceiver() throws InterruptedException {
        TestProgressBar delegate = new TestProgressBar();
        RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(delegate);
        int port = receiver.getPort();
        RemoteProgressBarSender sender = new RemoteProgressBarSender(port);

        try {
            // Send initial message
            sender.step();
            Thread.sleep(100);

            // Cancel directly on receiver
            receiver.setCanceled(true);
            assertTrue(receiver.isCanceled());

            // Send another message to trigger cancel propagation
            sender.setMax(100);
            Thread.sleep(100);

            // Sender should be canceled
            assertTrue(sender.isCanceled());

        } finally {
            sender.close();
            receiver.close();
        }
    }

    @Test
    @Timeout(10)
    public void testDelayedCancelCheck() throws InterruptedException {
        TestProgressBar delegate = new TestProgressBar();
        RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(delegate);
        int port = receiver.getPort();
        RemoteProgressBarSender sender = new RemoteProgressBarSender(port);

        try {
            // Send initial message to establish communication
            sender.step();
            Thread.sleep(100);

            // Cancel on receiver side
            receiver.setCanceled(true);

            // Wait 5 seconds before sender sends another message or checks
            Thread.sleep(5000);

            // Now check if sender picks up the cancel
            // Either by sending a message (which calls drainCancelMessages)
            sender.step();
            Thread.sleep(100);

            // Or by calling isCanceled (which also calls drainCancelMessages)
            assertTrue(sender.isCanceled(), "Sender should detect cancel even after 5 second delay");

        } finally {
            sender.close();
            receiver.close();
        }
    }

    @Test
    @Timeout(5)
    public void testCancelIsOneWayLatch() throws InterruptedException {
        TestProgressBar delegate = new TestProgressBar();
        RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(delegate);
        int port = receiver.getPort();
        RemoteProgressBarSender sender = new RemoteProgressBarSender(port);

        try {
            // Cancel and propagate
            delegate.setCanceled(true);
            sender.step();
            Thread.sleep(100);
            assertTrue(sender.isCanceled());

            // Try to uncancel - should not work
            delegate.setCanceled(false);
            receiver.setCanceled(false);

            // Send message
            sender.step();
            Thread.sleep(100);

            // Sender should still be canceled (one-way latch)
            assertTrue(sender.isCanceled());

        } finally {
            sender.close();
            receiver.close();
        }
    }


    // Test implementation of ProgressBar for testing
    static class TestProgressBar implements ProgressBar {
        private volatile boolean canceled = false;
        private int steps = 0;
        private int max = 0;

        @Override
        public void intermediateResult(String message) {
            // No-op
        }

        @Override
        public void finish(String message) {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }

        @Override
        public void step() {
            steps++;
        }

        @Override
        public ProgressBar setExtraMessage(String extraMessage) {
            return this;
        }

        @Override
        public ProgressBar setMax(int max) {
            this.max = max;
            return this;
        }

        @Override
        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        public int getSteps() {
            return steps;
        }

        public int getMax() {
            return max;
        }
    }
}