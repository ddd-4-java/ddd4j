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

    @Override
    public Class<SaInternalCheck> getHandlerAnnotationClass() {
        return SaInternalCheck.class;
    }

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
     */
    protected String readApiKey() {
        return ApiKeyKit.readApiKeyValue(SaHolder.getRequest());
    }

    /**
     * Validates API Key existence and required scopes.
     */
    protected void checkApiKey(String apiKey, String[] scopes) {
        ApiKeyKit.checkApiKey(apiKey);
        if (scopes.length > 0) {
            ApiKeyKit.checkApiKeyScope(apiKey, scopes);
        }
    }
}
