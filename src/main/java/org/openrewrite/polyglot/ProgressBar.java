/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
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
}
