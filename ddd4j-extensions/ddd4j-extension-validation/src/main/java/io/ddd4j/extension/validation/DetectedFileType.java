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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 根据文件内容识别出的真实文件类型。
 */

public final class DetectedFileType {

    private static final long serialVersionUID = 0L;

    private final String extension;

    private final String mimeType;

    /**
 * @param extension 真实扩展名，不含点号
 * @param mimeType 真实 MIME 类型
 */

    @JsonCreator()
    public DetectedFileType(@JsonProperty("extension") String extension, @JsonProperty("mimeType") String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    @JsonProperty("extension")
    public String extension() {
        return extension;
    }

    @JsonProperty("mimeType")
    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        DetectedFileType other = (DetectedFileType) obj;
        return Objects.equals(this.extension, other.extension) && Objects.equals(this.mimeType, other.mimeType);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(extension);
        result = 31 * result + Objects.hashCode(mimeType);
        return result;
    }

    @Override
    public String toString() {
        return "DetectedFileType[extension=" + extension + ", mimeType=" + mimeType + "]";
    }
}
