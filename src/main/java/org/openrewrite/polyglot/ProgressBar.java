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

public interface ProgressBar extends AutoCloseable {

    void intermediateResult(@Nullable String message);

    void finish(String message);

    @Override
    void close();

    void step();

    @SuppressWarnings("UnusedReturnValue")
    ProgressBar setExtraMessage(String extraMessage);

    ProgressBar setMax(int max);

    /**
     * Set the canceled state of the progress bar.
     * @param canceled true if the operation has been canceled
     */
    default void setCanceled(boolean canceled) {
        // Default no-op implementation for backward compatibility
    }

    /**
     * Check if the progress bar has been marked as canceled.
     * @return true if the operation has been canceled
     */
    default boolean isCanceled() {
        return false;
    }
}
