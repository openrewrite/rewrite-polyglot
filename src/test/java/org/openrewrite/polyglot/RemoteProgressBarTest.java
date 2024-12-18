/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.polyglot;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteProgressBarTest {

    @Test
    void remote() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(4);
        try (
          ProgressBar progressBar = new ProgressBar() {
              @Override
              public void intermediateResult(@Nullable String message) {
                  assertThat(message).isEqualTo("intermediate");
                  latch.countDown();
              }

              @Override
              public void finish(String message) {
              }

              @Override
              public void close() {
              }

              @Override
              public void step() {
                  latch.countDown();
              }

              @Override
              public ProgressBar setExtraMessage(String extraMessage) {
                  assertThat(extraMessage).isEqualTo("extra");
                  latch.countDown();
                  return this;
              }

              @Override
              public ProgressBar setMax(int max) {
                  assertThat(max).isEqualTo(100);
                  latch.countDown();
                  return this;
              }
          }) {
            try (RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(progressBar);
                 ProgressBar sender = new RemoteProgressBarSender(receiver.getPort())) {
                sender.setMax(100);
                sender.step();
                sender.setExtraMessage("extra");
                sender.intermediateResult("intermediate");
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void remoteException() {
        assertThatThrownBy(() -> {
            try (ProgressBar progressBar = new NoopProgressBar();
                 RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(progressBar)) {

                CountDownLatch latch = new CountDownLatch(1);
                new Thread(() -> {
                    try (RemoteProgressBarSender sender = new RemoteProgressBarSender(receiver.getPort())) {
                        sender.throwRemote(RemoteException.builder("boom").build());
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    latch.countDown();
                }).start();

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            }
        }).isInstanceOf(RemoteException.class);
    }
}
