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

import lombok.experimental.Delegate;
import org.openrewrite.SourceFile;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SourceFileStream implements Stream<SourceFile> {

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
