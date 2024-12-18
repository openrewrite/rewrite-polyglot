/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.polyglot;

import org.jspecify.annotations.Nullable;

public class NoopProgressBar implements ProgressBar {
    @Override
    public void intermediateResult(@Nullable String message) {
    }

    @Override
    public void finish(String message) {
    }

    @Override
    public void close() {
    }

    @Override
    public void step() {
    }

    @Override
    public ProgressBar setExtraMessage(String extraMessage) {
        return this;
    }

    @Override
    public ProgressBar setMax(int max) {
        return this;
    }
}
