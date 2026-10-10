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
 * 字段透明加密桥接契约。
 *
 * <p>具体 MyBatis 拦截器可消费本接口；它不依赖未发布的第三方增强库。</p>
 */
public interface EncryptedFieldHandler {

    /**
     * 加密字段值。
     *
     * @param <T>   原始值类型
     * @param value 待加密的原始值
     * @return 加密后的字符串
     */
    <T> String encrypt(T value);

    /**
     * 解密字段值。
     *
     * @param <T>        目标类型
     * @param value      密文字符串
     * @param resultType 目标类型
     * @return 解密后的对象
     */
    <T> T decrypt(String value, Class<T> resultType);

    /**
     * 计算字段值的密钥摘要。
     *
     * @param <T>   原始值类型
     * @param value 待计算的原始值
     * @return 摘要字符串
     */
    <T> String hmac(T value);
}
