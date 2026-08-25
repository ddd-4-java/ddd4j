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
package io.ddd4j.data.crypto.enums;

import cn.hutool.crypto.symmetric.SymmetricAlgorithm;

/**
 * 对称加密算法类型枚举
 * <p>定义系统支持的对称加密算法类型，包括 AES、DES、SM4 等</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum SymmetricAlgorithmType {

    /**
     * AES 对称加密算法
     */
    AES(SymmetricAlgorithm.AES.name()),
    /**
     * ARCFOUR 对称加密算法
     */
    ARCFOUR(SymmetricAlgorithm.ARCFOUR.name()),
    /**
     * Blowfish 对称加密算法
     */
    Blowfish(SymmetricAlgorithm.Blowfish.name()),
    /**
     * DES 对称加密算法
     */
    DES(SymmetricAlgorithm.DES.name()),
    /**
     * DESede 对称加密算法（三重 DES）
     */
    DESede(SymmetricAlgorithm.DESede.name()),
    /**
     * RC2 对称加密算法
     */
    RC2(SymmetricAlgorithm.RC2.name()),
    /**
     * PBEWithMD5AndDES 对称加密算法
     */
    PBEWithMD5AndDES(SymmetricAlgorithm.PBEWithMD5AndDES.name()),
    /**
     * PBEWithSHA1AndDESede 对称加密算法
     */
    PBEWithSHA1AndDESede(SymmetricAlgorithm.PBEWithSHA1AndDESede.name()),
    /**
     * PBEWithSHA1AndRC2_40 对称加密算法
     */
    PBEWithSHA1AndRC2_40(SymmetricAlgorithm.PBEWithSHA1AndRC2_40.name()),

    /**
     * 国密 SM4 对称加密算法
     */
    SM4("SM4");

    /**
     * 算法名称
     */
    private String name;

    /**
     * 构造函数
     *
     * @param name 算法名称
     */
    SymmetricAlgorithmType(String name) {
        this.name = name;
    }

    /**
     * 获取算法名称
     *
     * @return 算法名称字符串
     */
    public String getName() {
        return name;
    }

}
