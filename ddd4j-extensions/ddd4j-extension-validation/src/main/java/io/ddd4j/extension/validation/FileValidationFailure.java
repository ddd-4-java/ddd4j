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
 * 文件校验失败类型，可由各框架转换为统一业务错误码。
 */
public enum FileValidationFailure {
    /** 文件为空。 */
    EMPTY,
    /** 文件大小超过上限。 */
    SIZE_EXCEEDED,
    /** 扩展名不在允许范围内。 */
    EXTENSION_NOT_ALLOWED,
    /** MIME 类型不在允许范围内。 */
    MIME_TYPE_NOT_ALLOWED,
    /** 无法识别文件类型。 */
    TYPE_UNDETECTABLE,
    /** 文件内容与声明的扩展名不符。 */
    SIGNATURE_MISMATCH,
    /** 文件内容未通过检查。 */
    CONTENT_REJECTED,
    /** 读取文件失败。 */
    READ_ERROR
}
