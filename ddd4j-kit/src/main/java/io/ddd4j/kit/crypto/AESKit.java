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
package io.ddd4j.kit.crypto;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AES 对称加密工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class AESKit {

    /** \u590d\u7528\u7531\u6307\u5b9a\u5bc6\u94a5\u53ca\u521d\u59cb\u5316\u5411\u91cf\u521b\u5efa\u7684 AES \u8fd0\u7b97\u5bf9\u8c61\u3002 */
    private final Map<String, AES> aesMap = new ConcurrentHashMap<>();

    /**
     * 获取aes
     *
     * @param key 密钥，支持三种密钥长度：128、192、256位
     * @param iv  偏移向量，加盐
     * @return AES
     */
    public static AES getAes(String key, String iv) {
        return aesMap.computeIfAbsent(key + iv, k -> new AES(Mode.CBC, Padding.PKCS5Padding, key.getBytes(CharsetUtil.CHARSET_UTF_8),
                iv.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * AES-cbc\u52a0\u5bc6
     *
     * @param key \u5bc6\u94a5
     * @param iv \u521d\u59cb\u5316\u5411\u91cf
     * @param plainTxt \u660e\u6587
     * @return Base64 \u7f16\u7801\u7684\u5bc6\u6587
     */
    public static String encrypt(String key, String iv, String plainTxt) {
        SymmetricCrypto aes = getAes(key, iv);
        byte[] encrypHex = aes.encrypt(plainTxt);
        return Base64.encode(encrypHex);
    }

    /**
     * AES-cbc\u89e3\u5bc6
     *
     * @param key \u5bc6\u94a5
     * @param iv \u521d\u59cb\u5316\u5411\u91cf
     * @param cipherTxt Base64 \u7f16\u7801\u7684\u5bc6\u6587
     * @return \u89e3\u5bc6\u540e\u7684\u660e\u6587
     */
    public static String decrypt(String key, String iv, String cipherTxt) {
        SymmetricCrypto aes = getAes(key, iv);
        byte[] cipherHex = Base64.decode(cipherTxt.trim());
        return aes.decryptStr(cipherHex, CharsetUtil.CHARSET_UTF_8);
    }

}
