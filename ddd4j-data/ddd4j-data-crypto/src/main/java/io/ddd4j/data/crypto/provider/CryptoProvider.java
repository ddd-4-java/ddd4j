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
package io.ddd4j.data.crypto.provider;

/**
 * 加解密提供者
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface CryptoProvider {

    /**
     * 字段加密
     *
     * @param value 待加密字段的值
     * @param <T>   字段类型
     * @return T 加密后的字段值
     */
    <T> String encrypt(T value);

    /**
     * 字段解密
     *
     * @param value 待解密字段的值
     * @param <T>   字段类型
     * @return T 解密后的字段值
     */
    <T> T decrypt(String value, Class<T> rtType);

    /**
     * hmac 签名
     *
     * @param value 待签名的值
     * @param <T>   字段类型
     * @return 签名后的字符串
     */
    <T> String hmac(T value);

}
