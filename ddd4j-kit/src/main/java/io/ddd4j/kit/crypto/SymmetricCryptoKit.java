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

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SM4;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对称加密工具类（统一封装 SM4/AES/HMAC）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class SymmetricCryptoKit {

    private final Map<String, SymmetricCrypto> SYMMETRIC_CRYPTO_CACHE = new ConcurrentHashMap<>();
    private final Map<String, HMac> HMAC_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取 SymmetricCrypto
     *
     * @param algorithmType 算法类型（SM4/AES）
     * @param mode          加密模式（ECB/CBC）
     * @param padding       填充方式（PKCS5Padding/PKCS7Padding）
     * @param key           密钥
     * @param iv            偏移向量，加盐
     * @return SymmetricCrypto
     */
    public static SymmetricCrypto getSymmetricCrypto(String algorithmType, String mode, String padding, String key, String iv) {
        StringJoiner keyJoiner = new StringJoiner("_").add(algorithmType).add(mode).add(padding).add(key).add(iv);
        // 构造对称加密器
        return SYMMETRIC_CRYPTO_CACHE.computeIfAbsent(keyJoiner.toString(), join -> {
            String[] keyArr = join.split("_");
            String algorithmTypeStr = Objects.toString(keyArr[0], SM4.ALGORITHM_NAME);
            String modeStr = Objects.toString(keyArr[1], Mode.ECB.name());
            String paddingStr = Objects.toString(keyArr[2], Padding.PKCS5Padding.name());
            byte[] keyBytes = StringUtils.isBlank(keyArr[3]) ? null : keyArr[3].getBytes(CharsetUtil.CHARSET_UTF_8);
            byte[] ivBytes = StringUtils.isBlank(keyArr[4]) ? null : keyArr[4].getBytes(CharsetUtil.CHARSET_UTF_8);
            // 构造SM4加密器
            if (SM4.ALGORITHM_NAME.equalsIgnoreCase(algorithmType)) {
                return new SM4(Mode.valueOf(modeStr), Padding.valueOf(keyArr[2]), keyBytes, ivBytes);
            }
            // 构造AES加密器
            if (SymmetricAlgorithm.AES.name().equalsIgnoreCase(algorithmType)) {
                return new AES(Mode.valueOf(modeStr), Padding.valueOf(paddingStr), keyBytes, ivBytes);
            }

            return new AES(Mode.valueOf(modeStr), Padding.valueOf(keyArr[2]), keyBytes, ivBytes);
        });
    }

    /**
     * 获取 SM4
     *
     * @param mode    加密模式
     * @param padding 填充方式
     * @param key     密钥
     * @param iv      偏移向量
     * @return SymmetricCrypto
     */
    public static SymmetricCrypto getSm4(String mode, String padding, String key, String iv) {
        return getSymmetricCrypto(SM4.ALGORITHM_NAME, mode, padding, key, iv);
    }

    /**
     * 获取 AES
     *
     * @param mode    加密模式
     * @param padding 填充方式
     * @param key     密钥
     * @param iv      偏移向量
     * @return SymmetricCrypto
     */
    public static SymmetricCrypto getAes(String mode, String padding, String key, String iv) {
        return getSymmetricCrypto(SymmetricAlgorithm.AES.name(), mode, padding, key, iv);
    }

    /**
     * 获取 HMac
     *
     * @param hmacAlgorithm Hmac算法
     * @param key           密钥
     * @return HMac
     */
    public static HMac getHmac(HmacAlgorithm hmacAlgorithm, String key) {
        StringJoiner keyJoiner = new StringJoiner("_").add(hmacAlgorithm.getValue()).add(key);
        // 构造对称加密器
        return HMAC_CACHE.computeIfAbsent(keyJoiner.toString(), join -> {
            String[] keyArr = join.split("_");
            String hmacAlgorithmStr = Objects.toString(keyArr[0], HmacAlgorithm.HmacSM3.getValue());
            byte[] keyBytes = keyArr[1].getBytes(CharsetUtil.CHARSET_UTF_8);
            return new HMac(hmacAlgorithmStr, keyBytes);
        });
    }

}
