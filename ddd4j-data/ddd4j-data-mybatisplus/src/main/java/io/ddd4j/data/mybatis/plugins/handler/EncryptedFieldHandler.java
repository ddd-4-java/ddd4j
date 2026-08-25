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
package io.ddd4j.data.mybatis.plugins.handler;

/**
 * MyBatis-Plus 字段透明加密桥接契约。
 *
 * <p>由 ddd4j 自己维护，避免把未发布增强库的接口暴露给应用代码。</p>
 */
public interface EncryptedFieldHandler {

    <T> String encrypt(T value);

    <T> T decrypt(String value, Class<T> resultType);

    <T> String hmac(T value);
}
