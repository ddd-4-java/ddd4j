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
package io.ddd4j.core.api;

import io.ddd4j.core.exception.BizRuntimeException;

import java.io.Serializable;
import java.util.Objects;

/**
 * 统一接口响应，标准的响应数据结构
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface IR extends Serializable {
    Serializable getCode();

    String getMsg();

    <T> T getData();

    Boolean isOk();

    default void isOk(String notOkThrows) {
        if (!isOk()) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
    }

    default <T> T getData(String notOkThrows) {
        if (!isOk()) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
        T data = getData();
        if (Objects.isNull(data)) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
        return data;
    }
}