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
    EMPTY,
    SIZE_EXCEEDED,
    EXTENSION_NOT_ALLOWED,
    MIME_TYPE_NOT_ALLOWED,
    TYPE_UNDETECTABLE,
    SIGNATURE_MISMATCH,
    CONTENT_REJECTED,
    READ_ERROR
}
