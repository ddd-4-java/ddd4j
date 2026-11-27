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
package io.ddd4j.auth.satoken.handler;

import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.auth.satoken.annotation.SaInternalCheck;
import io.ddd4j.auth.satoken.util.ApiKeyKit;
import io.ddd4j.kit.lang.StrKit;

import java.lang.reflect.AnnotatedElement;

/**
 * Sa-Token annotation handler for internal service API Key access.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SaInternalCheckHandler implements SaAnnotationHandlerInterface<SaInternalCheck> {

    /**
     * 声明由当前处理器解释的内部认证注解。
     *
     * @return 内部 API 校验注解的类型
     */
    @Override
    public Class<SaInternalCheck> getHandlerAnnotationClass() {
        return SaInternalCheck.class;
    }

    /**
     * 读取当前请求的 API 密钥并按注解范围执行内部认证。
     *
     * @param at      声明当前入口认证要求的注解
     * @param element 触发认证检查的注解元素
     * @throws SaTokenException 请求缺少密钥或未通过内部检查
     */
    @Override
    public void checkMethod(SaInternalCheck at, AnnotatedElement element) {
        String apiKey = readApiKey();
        if (StrKit.isBlank(apiKey)) {
            throw new SaTokenException("Missing internal API key");
        }
        checkApiKey(apiKey, at.scope());
    }

    /**
     * Reads the API Key using Sa-Token's configured request strategy.
     *
     * @return current request API Key
     */
    protected String readApiKey() {
        return ApiKeyKit.readApiKeyValue(SaHolder.getRequest());
    }

    /**
     * Validates API Key existence and required scopes.
     *
     * @param apiKey API Key to validate
     * @param scopes required scopes
     */
    protected void checkApiKey(String apiKey, String[] scopes) {
        ApiKeyKit.checkApiKey(apiKey);
        if (scopes.length > 0) {
            ApiKeyKit.checkApiKeyScope(apiKey, scopes);
        }
    }
}
