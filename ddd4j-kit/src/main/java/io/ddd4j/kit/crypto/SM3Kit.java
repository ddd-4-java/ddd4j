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
import cn.hutool.crypto.digest.SM3;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SM3 摘要签名算法工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class SM3Kit {

    private final Map<String, SM3> sm3Map = new ConcurrentHashMap<>();

    /**
     * 获取SM3
     *
     * @param salt 盐值
     * @return SM3
     */
    public static SM3 getSm3(String salt) {
        return sm3Map.computeIfAbsent(salt, k -> new SM3(salt.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * SM3-摘要
     */
    public static String digest(String salt, String plainTxt) {
        SM3 sm3 = getSm3(salt);
        return sm3.digestHex(plainTxt);
    }

}
