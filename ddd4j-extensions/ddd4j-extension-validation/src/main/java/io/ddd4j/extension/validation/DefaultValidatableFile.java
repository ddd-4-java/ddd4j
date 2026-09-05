/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 框架适配层可直接使用的不可变文件对象。
 */
public final class DefaultValidatableFile implements ValidatableFile {

    private final String fileName;
    private final String contentType;
    private final long size;
    private final InputStreamSource inputStreamSource;

    public DefaultValidatableFile(String fileName, String contentType, long size,
            InputStreamSource inputStreamSource) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.inputStreamSource = Objects.requireNonNull(inputStreamSource, "inputStreamSource must not be null");
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public InputStream openStream() throws IOException {
        return inputStreamSource.openStream();
    }
}
