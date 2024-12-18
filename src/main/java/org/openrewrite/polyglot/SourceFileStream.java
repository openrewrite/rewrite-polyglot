/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.polyglot;

import lombok.experimental.Delegate;
import org.openrewrite.SourceFile;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SourceFileStream implements Stream<SourceFile> {

    @Delegate
    private final Stream<SourceFile> delegate;

    private final String group;
    private final Consumer<String> peekGroup;
    private final int size;

    public static SourceFileStream build(String group, Consumer<String> peekGroup) {
        Set<String> seenGroup = new HashSet<>();
        return new SourceFileStream(Stream.empty(), 0, group, g -> {
            if (seenGroup.add(g)) {
                peekGroup.accept(g);
            }
        });
    }

    private SourceFileStream(Stream<SourceFile> delegate, int size, String group, Consumer<String> peekGroup) {
        this.size = size;
        this.delegate = delegate.peek(sf -> peekGroup.accept(group));
        this.group = group;
        this.peekGroup = peekGroup;
    }

    public SourceFileStream concat(SourceFileStream sourceFileStream) {
        return new SourceFileStream(Stream.concat(delegate, sourceFileStream),
                sourceFileStream.size + this.size, group, peekGroup);
    }

    public SourceFileStream concat(Stream<SourceFile> sourceFileStream, int size) {
        return new SourceFileStream(Stream.concat(delegate, sourceFileStream),
                size + this.size, group, peekGroup);
    }

    public int size() {
        return size;
    }
}
