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
 * 文件校验结果。
 *
 * @param valid 是否通过
 * @param failure 失败原因，通过时为空
 * @param detectedType 内容检测结果，可以为空
 */
public final class FileValidationResult {
    private final boolean valid;
    private final FileValidationFailure failure;
    private final DetectedFileType detectedType;

    public FileValidationResult(boolean valid, FileValidationFailure failure, DetectedFileType detectedType) {

        this.valid = valid;
        this.failure = failure;
        this.detectedType = detectedType;
    }

    public boolean valid() {
        return valid;
    }

    public FileValidationFailure failure() {
        return failure;
    }

    public DetectedFileType detectedType() {
        return detectedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileValidationResult)) return false;
        FileValidationResult that = (FileValidationResult) o;
        return Objects.equals(valid, that.valid) && Objects.equals(failure, that.failure) && Objects.equals(detectedType, that.detectedType);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(valid);
        result = 31 * result + Objects.hashCode(failure);
        result = 31 * result + Objects.hashCode(detectedType);
        return result;
    }

    @Override
    public String toString() {
        return "FileValidationResult{" + valid + ", " + failure + ", " + detectedType + '}';
    }


    /**
     * 创建成功结果。
     *
     * @param detectedType 检测类型
     * @return 成功结果
     */
    public static FileValidationResult valid(DetectedFileType detectedType) {
        return new FileValidationResult(true, null, detectedType);
    }

    /**
     * 创建失败结果。
     *
     * @param failure 失败原因
     * @param detectedType 已检测类型
     * @return 失败结果
     */
    public static FileValidationResult invalid(FileValidationFailure failure, DetectedFileType detectedType) {
        return new FileValidationResult(false, failure, detectedType);
    }


    public boolean isValid() {
        return valid;
    }

    public FileValidationFailure getFailure() {
        return failure;
    }

    public DetectedFileType getDetectedType() {
        return detectedType;
    }
}
