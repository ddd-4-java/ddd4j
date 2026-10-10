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
    /** 文件大小超出允许上限。 */
    SIZE_EXCEEDED,
    /** 文件扩展名不在允许列表内。 */
    EXTENSION_NOT_ALLOWED,
    /** 文件 MIME 类型不在允许列表内。 */
    MIME_TYPE_NOT_ALLOWED,
    /** 无法从文件内容识别出类型。 */
    TYPE_UNDETECTABLE,
    /** 文件签名与声明的类型不一致。 */
    SIGNATURE_MISMATCH,
    /** 文件内部业务内容校验未通过。 */
    CONTENT_REJECTED,
    /** 读取文件内容时发生错误。 */
    READ_ERROR
}
