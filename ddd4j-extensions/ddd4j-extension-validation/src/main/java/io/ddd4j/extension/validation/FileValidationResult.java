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

/**
 * 文件校验结果。
 *
 * @param valid 是否通过
 * @param failure 失败原因，通过时为空
 * @param detectedType 内容检测结果，可以为空
 */
public record FileValidationResult(boolean valid, FileValidationFailure failure, DetectedFileType detectedType) {

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
}
