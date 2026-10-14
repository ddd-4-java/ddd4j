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

    /**
     * 获取响应编码。
     *
     * @return 响应编码（数值或字符串形式）
     */
    Serializable getCode();

    /**
     * 获取响应提示信息。
     *
     * @return 提示信息
     */
    String getMsg();

    /**
     * 获取响应数据。
     *
     * @param <T> 数据类型
     * @return 响应数据
     */
    <T> T getData();

    /**
     * 判断响应是否成功。
     *
     * @return true 表示成功
     */
    Boolean isOk();

    /**
     * 响应不成功时抛出业务异常，成功时无操作。
     *
     * @param notOkThrows 失败时的提示信息
     */
    default void isOk(String notOkThrows) {
        if (!isOk()) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
    }

    /**
     * 响应成功且数据非空时返回数据，否则抛出业务异常。
     *
     * @param notOkThrows 失败时的提示信息
     * @param <T>         数据类型
     * @return 响应数据
     */
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
