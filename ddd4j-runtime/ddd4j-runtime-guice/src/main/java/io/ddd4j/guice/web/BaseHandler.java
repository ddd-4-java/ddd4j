/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.guice.web;

import java.util.Objects;

import io.ddd4j.core.context.I18nProvider;
import io.ddd4j.core.contract.DomainEvent;
import io.ddd4j.core.contract.DomainEventPublisher;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.guice.util.WebKit;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * Javalin 基础处理器（等价于 Spring 的 BaseController）。
 * <p>
 * 集成 i18n（getMessage）、事件发布（logException）等基础能力。
 * 业务 Handler 应继承此类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public abstract class BaseHandler {

    /**
     * 获取国际化消息
     */
    protected String getMessage(String code, Object... args) {
        I18nProvider i18n = GuiceContext.getInstance(I18nProvider.class);
        return i18n.getMessage(code, args);
    }

    /**
     * 获取国际化消息（指定 Locale）
     */
    protected String getMessage(String code, Locale locale, Object... args) {
        I18nProvider i18n = GuiceContext.getInstance(I18nProvider.class);
        return i18n.getMessage(code, locale, args);
    }

    /**
     * 记录异常并发布领域事件
     */
    protected void logException(Context ctx, Exception ex) {
        log.error("Exception in request [{} {}]: {}", ctx.method(), ctx.url(), ex.getMessage(), ex);
        try {
            DomainEventPublisher publisher = GuiceContext.getInstance(DomainEventPublisher.class);
            if (Objects.nonNull(publisher)) {
                // 创建异常事件并发布
                DomainEvent<Exception> event = new ExceptionEvent(ex, ctx.url());
                publisher.publish(event);
            }
        } catch (Exception e) {
            log.warn("Failed to publish exception event", e);
        }
    }

    /**
     * 获取客户端 IP
     */
    protected String getClientIp(Context ctx) {
        return WebKit.getClientIp(ctx);
    }

    /**
     * 判断是否为 AJAX 请求
     */
    protected boolean isAjax(Context ctx) {
        return WebKit.isAjax(ctx);
    }

    /**
     * 异常领域事件
     */
    public static class ExceptionEvent extends DomainEvent<Exception> {
        private static final long serialVersionUID = 1L;
        private final String requestUrl;

        public ExceptionEvent(Exception source, String requestUrl) {
            super(source);
            this.requestUrl = requestUrl;
        }

        public String getRequestUrl() {
            return requestUrl;
        }
    }
}
