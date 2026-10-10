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
package io.ddd4j.extension.pf4j.point.crypto;

import org.pf4j.ExtensionPoint;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface CryptoExtensionPoint extends ExtensionPoint {

    /**
     * 使用指定密钥对明文做加密。
     *
     * @param source    待加密的明文
     * @param secretKey 加密密钥
     * @return 加密后的密文
     */
    String encrypt(String source, Object secretKey);

    /**
     * 使用指定密钥对密文做解密。
     *
     * @param source    待解密的密文
     * @param secretKey 解密密钥
     * @return 解密后的明文
     */
    String decrypt(String source, Object secretKey);

}
