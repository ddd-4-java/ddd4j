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

import java.util.Objects;

/**
 * 根据文件内容识别出的真实文件类型。
 *
 * @param extension 真实扩展名，不含点号
 * @param mimeType 真实 MIME 类型
 */
public final class DetectedFileType {
    private final String extension;
    private final String mimeType;

    public DetectedFileType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() { return extension; }
    public String mimeType() { return mimeType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetectedFileType)) return false;
        DetectedFileType that = (DetectedFileType) o;
        return Objects.equals(extension, that.extension) && Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extension, mimeType);
    }

    @Override
    public String toString() {
        return "DetectedFileType{extension=" + extension + ", mimeType=" + mimeType + '}';
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
    }
}
