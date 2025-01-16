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

import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

/**
 * A serialized exception DTO that contains enough information about an exception caused by a
 * remote process to render a meaningful message, list of suggestions, and optionally a stack trace.
 */
@Getter
public class RemoteException extends RuntimeException {
    @Nullable
    private final String sanitizedStackTrace;

    private final List<String> fixSuggestions;

    private final boolean partialSuccess;

    RemoteException(String message,
                    @Nullable String sanitizedStackTrace,
                    String[] fixSuggestions,
                    boolean partialSuccess) {
        super(message);
        this.sanitizedStackTrace = sanitizedStackTrace;
        this.fixSuggestions = new ArrayList<>(Arrays.asList(fixSuggestions));
        this.partialSuccess = partialSuccess;
    }

    public static Builder builder(String message, String... stackTracePrefixFilter) {
        return new Builder(message, stackTracePrefixFilter);
    }

    public static class Builder {
        private final String message;
        private final List<String> fixSuggestions = new ArrayList<>();
        private String sanitizedStackTrace;
        private boolean partialSuccess;

        public Builder(String message, String... stackTracePrefixFilter) {
            this.message = message;
            StringJoiner sanitized = new StringJoiner("\n");
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StackTraceElement[] shortenedStackTrace = new StackTraceElement[stackTrace.length - 3];
            System.arraycopy(stackTrace, 3, shortenedStackTrace, 0, stackTrace.length - 3);
            sanitizeStackElements(sanitized, shortenedStackTrace, stackTracePrefixFilter);
            this.sanitizedStackTrace = sanitized.toString();
        }

        public Builder cause(Throwable t, String... stackTracePrefixFilter) {
            this.sanitizedStackTrace = sanitizeStackTrace(t, stackTracePrefixFilter);
            return this;
        }

        public Builder fixSuggestions(String... fixSuggestions) {
            return fixSuggestions(Arrays.asList(fixSuggestions));
        }

        public Builder fixSuggestions(List<String> fixSuggestions) {
            this.fixSuggestions.addAll(fixSuggestions);
            return this;
        }

        public Builder partialSuccess(boolean partialSuccess) {
            this.partialSuccess = partialSuccess;
            return this;
        }

        public RemoteException build() {
            return new RemoteException(
                    message,
                    sanitizedStackTrace,
                    fixSuggestions.toArray(new String[0]),
                    partialSuccess);
        }
    }

    public static String sanitizeStackTrace(Throwable t, String... stackTracePrefixFilter) {
        StringJoiner sanitized = new StringJoiner("\n");

        int causeDepth = 0;
        for (Throwable tt = t; tt != null; tt = tt.getCause(), causeDepth++) {
            sanitized.add((causeDepth == 0 ? "" : "Caused by ") +
                          tt.getClass().getName() + ": " + tt.getLocalizedMessage());
            sanitizeStackElements(sanitized, tt.getStackTrace(), stackTracePrefixFilter);
        }
        return sanitized.toString();
    }

    private static void sanitizeStackElements(StringJoiner sanitized, StackTraceElement[] stackTraceElements, String[] stackTracePrefixFilter) {
        int i = 0;
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            String stackTraceClass = stackTraceElement.getClassName();
            if (stackTraceClass.startsWith("java.util.stream") ||
                stackTraceClass.startsWith("java.net.Inet")) {
                break;
            }
            for (String filter : stackTracePrefixFilter) {
                if (stackTraceClass.startsWith(filter)) {
                    break;
                }
            }
            if (i++ >= 8) {
                sanitized.add("  ...");
                break;
            }
            sanitized.add("  " + stackTraceElement);
        }
    }

    public String encode() {
        Base64.Encoder base64 = Base64.getEncoder();
        StringBuilder builder = new StringBuilder(256);
        builder.append(base64.encodeToString(getMessage().getBytes(UTF_8))).append('\n');
        if (sanitizedStackTrace != null) {
            builder.append(base64.encodeToString(sanitizedStackTrace.getBytes(UTF_8))).append('\n');
        } else {
            builder.append("null").append('\n');
        }
        if (!fixSuggestions.isEmpty()) {
            String suggestions = fixSuggestions.stream()
                    .map(s -> s.getBytes(UTF_8))
                    .map(base64::encodeToString)
                    .collect(joining(","));
            builder.append(suggestions).append('\n');
        } else {
            builder.append("null").append('\n');
        }
        builder.append(partialSuccess);
        return builder.toString();
    }

    public static RemoteException decode(String encoded) {
        Base64.Decoder base64 = Base64.getDecoder();
        String[] lines = encoded.split("\n");
        String message = new String(base64.decode(lines[0]), UTF_8);
        String stackTrace = "null".equals(lines[1]) ? null : new String(base64.decode(lines[1]), UTF_8);
        String[] suggestions = "null".equals(lines[2]) ?
                new String[0] :
                stream(lines[2].split(",")).map(s -> new String(base64.decode(s), UTF_8)).toArray(String[]::new);
        boolean partialSuccess = lines.length > 3 ? Boolean.parseBoolean(lines[3]) : false;
        return new RemoteException(message, stackTrace, suggestions, partialSuccess);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        s.println(this);
        if (sanitizedStackTrace != null) {
            s.println(sanitizedStackTrace);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RemoteException that = (RemoteException) o;
        return Objects.equals(sanitizedStackTrace, that.sanitizedStackTrace) && Objects.equals(fixSuggestions, that.fixSuggestions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sanitizedStackTrace);
        result = 31 * result + Objects.hashCode(fixSuggestions);
        return result;
    }
}
