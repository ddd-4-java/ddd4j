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
 * \u52a0\u89e3\u5bc6\uff08Crypto\uff09\u6269\u5c55\u70b9\u3002
 *
 *  <p>\u63d2\u4ef6\u5b9e\u73b0\u8be5\u63a5\u53e3\uff0c\u4e3a\u4e3b\u63a7\u63d0\u4f9b\u660e\u6587\u52a0\u5bc6\u4e0e\u5bc6\u6587\u89e3\u5bc6\u80fd\u529b\u3002
 * @since 2.0.x
 */
public interface CryptoExtensionPoint extends ExtensionPoint {

    /**
     * \u52a0\u5bc6\u660e\u6587\u3002
     * @param source \u5f85\u52a0\u5bc6\u660e\u6587
     * @param secretKey \u5bc6\u94a5
     * @return \u5bc6\u6587
     */
    String encrypt(String source, Object secretKey);

    /**
     * \u89e3\u5bc6\u5bc6\u6587\u3002
     * @param source \u5f85\u89e3\u5bc6\u5bc6\u6587
     * @param secretKey \u5bc6\u94a5
     * @return \u660e\u6587
     */
    String decrypt(String source, Object secretKey);

}
