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
     * 获取响应码
     * @return 响应码
     */
    Serializable getCode();

    /**
     * 获取响应消息
     * @return 响应消息
     */
    String getMsg();

    /**
     * 获取响应数据
     * @param <T> 响应数据类型
     * @return 响应数据
     */
    <T> T getData();

    /**
     * 是否响应成功
     * @return 布尔值，表示响应是否成功
     */
    Boolean isOk();

    /**
     * 是否响应成功
     * @param notOkThrows 响应失败抛出异常消息
     */
    default void isOk(String notOkThrows) {
        if (!isOk()) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
    }

    /**
     * 获取响应数据
     * @param notOkThrows 响应失败抛出异常消息
     * @param <T> 响应数据类型
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