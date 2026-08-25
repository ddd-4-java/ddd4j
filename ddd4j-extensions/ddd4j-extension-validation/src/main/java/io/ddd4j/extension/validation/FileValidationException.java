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
 * 文件未通过校验时由适配层使用的异常。
 */
public class FileValidationException extends RuntimeException {

    private final FileValidationFailure failure;

    public FileValidationException(FileValidationFailure failure) {
        super("Uploaded file validation failed: " + Objects.requireNonNull(failure, "failure must not be null"));
        this.failure = failure;
    }

    public FileValidationFailure getFailure() {
        return failure;
    }
}
