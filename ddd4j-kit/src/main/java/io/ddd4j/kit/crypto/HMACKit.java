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
import cn.hutool.crypto.digest.HMac;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HMAC 摘要签名算法工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class HMACKit {

    private final Map<String, HMac> hMacMap = new ConcurrentHashMap<>();

    /**
     * 获取 HMac
     *
     * @param algorithm 摘要算法 HmacMD5、HmacSHA1、HmacSHA256、HmacSHA384、HmacSHA512
     * @param salt      盐值
     * @return HMac
     */
    public static HMac getHMac(String algorithm, String salt) {
        return hMacMap.computeIfAbsent(salt, k -> new HMac(algorithm, salt.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * HMac-摘要
     */
    public static String digest(String algorithm, String salt, String plainTxt) {
        HMac hMac = getHMac(algorithm, salt);
        return hMac.digestHex(plainTxt);
    }

}
