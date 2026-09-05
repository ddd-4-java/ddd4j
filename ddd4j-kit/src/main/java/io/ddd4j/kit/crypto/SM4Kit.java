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
import cn.hutool.crypto.symmetric.SM4;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SM4 对称加密工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class SM4Kit {

    private final Map<String, SM4> sm4Map = new ConcurrentHashMap<>();

    /**
     * 获取SM4
     *
     * @param key 密钥，支持三种密钥长度：128、192、256位
     * @param iv  偏移向量，加盐
     * @return SM4
     */
    public static SM4 getSm4(String key, String iv) {
        return sm4Map.computeIfAbsent(key + iv, k -> new SM4(Mode.CBC, Padding.PKCS5Padding, key.getBytes(CharsetUtil.CHARSET_UTF_8),
                iv.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * SM4-cbc加密
     */
    public static String encrypt(String key, String iv, String plainTxt) {
        SymmetricCrypto sm4 = getSm4(key, iv);
        byte[] encrypHex = sm4.encrypt(plainTxt);
        return Base64.encode(encrypHex);
    }

    /**
     * SM4-cbc解密
     */
    public static String decrypt(String key, String iv, String cipherTxt) {
        SymmetricCrypto sm4 = getSm4(key, iv);
        byte[] cipherHex = Base64.decode(cipherTxt.trim());
        return sm4.decryptStr(cipherHex, CharsetUtil.CHARSET_UTF_8);
    }

}
