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
package org.openrewrite.polyglot.interop;

import com.google.auto.service.AutoService;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

@AutoService(FileSystemProvider.class)
public class StringFileSystemProvider extends FileSystemProvider {
    @Override
    public String getScheme() {
        return "string";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NotNull
    @Override
    public Path getPath(@NotNull URI uri) {
        return Paths.get(uri.getPath());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) {

    }

    @Override
    public void delete(Path path) {

    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) {

    }

    @Override
    public void move(Path source, Path target, CopyOption... options) {

    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
        return false;
    }

    @Override
    public boolean isHidden(Path path) {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) {

    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
    }
}
