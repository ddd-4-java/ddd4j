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
package io.ddd4j.data.mybatis.plugins.handler;

import cn.hutool.crypto.digest.HmacAlgorithm;
import io.ddd4j.data.crypto.enums.SymmetricAlgorithmType;
import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;

/**
 * 将 ddd4j-data-crypto 的 {@link CryptoStrategy} 桥接为 ddd4j 的 {@link EncryptedFieldHandler}。
 *
 * <p>业务方只需提供 {@link CryptoStrategy} 实现（DefaultCryptoStrategy / FlksecCryptoStrategy / NoOpCryptoStrategy），
 * 具体字段注解与拦截器由应用在 MyBatis-Plus 标准扩展点中注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultEncryptedFieldHandler implements EncryptedFieldHandler {

    private final CryptoStrategy cryptoStrategy;

    public DefaultEncryptedFieldHandler(CryptoStrategy cryptoStrategy) {
        this.cryptoStrategy = Objects.requireNonNull(cryptoStrategy, "cryptoStrategy must not be null");
    }

    @Override
    public <T> String encrypt(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return cryptoStrategy.encrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false);
    }

    @Override
    public <T> T decrypt(String value, Class<T> rtType) {
        if (StrKit.isEmpty(value)) {
            return null;
        }
        return cryptoStrategy.decrypt(value, SymmetricAlgorithmType.SM4, null, null, null, null, false, rtType);
    }

    @Override
    public <T> String hmac(T value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return cryptoStrategy.hmac(value, HmacAlgorithm.HmacSM3, null, null, false);
    }

}
