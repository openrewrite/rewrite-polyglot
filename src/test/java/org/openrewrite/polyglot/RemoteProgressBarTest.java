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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.internal.lang.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteProgressBarTest {

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
          };

          RemoteProgressBarReceiver receiver = new RemoteProgressBarReceiver(progressBar)) {
            try (ProgressBar sender = new RemoteProgressBarSender(receiver.getPort())) {
                sender.setMax(100);
                sender.step();
                sender.setExtraMessage("extra");
                sender.intermediateResult("intermediate");
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("longStrings")
    void truncate(String input, String output) {
        int maxLength = 255;
        String actual = RemoteProgressBarSender.truncateMessage(input, maxLength);
        assertThat(actual).isEqualTo(output);
        if (maxLength <= input.length()) {
            assertThat(actual).hasSize(maxLength);
        } else {
            assertThat(actual).hasSize(input.length());
        }
    }

    private static Stream<Arguments> longStrings() {
        String twohunderdfifty = "1234567890".repeat(25);
        return Stream.of(
          Arguments.of(twohunderdfifty + "123", twohunderdfifty + "123"),
          Arguments.of(twohunderdfifty + "1234", twohunderdfifty + "1234"),
          Arguments.of(twohunderdfifty + "12345", twohunderdfifty + "12345"),
          Arguments.of(twohunderdfifty + "123456", "..." + twohunderdfifty.substring(4) + "123456"),
          Arguments.of(twohunderdfifty + "1234567", "..." + twohunderdfifty.substring(5) + "1234567"),
          Arguments.of(twohunderdfifty + "12345678", "..." + twohunderdfifty.substring(6) + "12345678"),
          Arguments.of(twohunderdfifty + "123456789", "..." + twohunderdfifty.substring(7) + "123456789"),
          Arguments.of(twohunderdfifty + "1234567890", "..." + twohunderdfifty.substring(8) + "1234567890")
        );
    }

}
