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
 * 加解密（Crypto）扩展点。
 *
 * <p>插件实现该接口，为主控提供明文加密与密文解密能力。
 *
 * @since 2.0.x
 */
public interface CryptoExtensionPoint extends ExtensionPoint {

    /**
     * 加密明文。
     *
     * @param source    待加密明文
     * @param secretKey 密钥
     * @return 密文
     */
    String encrypt(String source, Object secretKey);

    /**
     * 解密密文。
     *
     * @param source    待解密密文
     * @param secretKey 密钥
     * @return 明文
     */
    String decrypt(String source, Object secretKey);

}
