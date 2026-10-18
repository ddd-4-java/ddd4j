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
package io.ddd4j.web.javalin;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.web.core.context.WebRequestFailure;
import io.ddd4j.web.javalin.util.WebKit;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

/**
 * Javalin Handler 可选基类，只依赖 ddd4j SPI，不依赖 Guice 或 Spring。
 */
@Slf4j
public abstract class BaseHandler {

    /**
     * 解析本地化消息。
     *
     * @param code 消息键
     * @param args 消息参数
     * @return 本地化消息
     */
    protected String getMessage(String code, Object... args) {
        return Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class)
                .orElse(I18nProvider.DEFAULT)
                .getMessage(code, args);
    }

    /**
     * 记录请求异常并发布失败事件。
     *
     * @param context Javalin 请求上下文
     * @param exception 捕获的异常
     */
    protected void logException(Context context, Exception exception) {
        log.error("Exception in request [{} {}]", context.method(), context.path(), exception);
        Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)
                .ifPresent(publisher -> publisher.publish(
                        new WebRequestFailure(context.method().name(), context.path(), exception)));
    }

    /**
     * 获取请求客户端地址。
     *
     * @param context Javalin 请求上下文
     * @return 客户端地址
     */
    protected String getClientIp(Context context) {
        return WebKit.getClientIp(context);
    }

    /**
     * 判断请求是否为 AJAX 请求。
     *
     * @param context Javalin 请求上下文
     * @return 是否为 AJAX 请求
     */
    protected boolean isAjax(Context context) {
        return WebKit.isAjax(context);
    }
}
