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
     * \u89e3\u6790\u672c\u5730\u5316\u6d88\u606f\u3002
     * @param code \u6d88\u606f\u952e
     * @param args \u6d88\u606f\u53c2\u6570
     * @return \u672c\u5730\u5316\u6d88\u606f
     */
    protected String getMessage(String code, Object... args) {
        return Contexts.get(SpiKeys.I18N_PROVIDER, I18nProvider.class)
                .orElse(I18nProvider.DEFAULT)
                .getMessage(code, args);
    }

    /**
     * \u8bb0\u5f55\u8bf7\u6c42\u5f02\u5e38\u5e76\u53d1\u5e03\u5931\u8d25\u4e8b\u4ef6\u3002
     * @param context Javalin \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param exception \u6355\u83b7\u7684\u5f02\u5e38
     */
    protected void logException(Context context, Exception exception) {
        log.error("Exception in request [{} {}]", context.method(), context.path(), exception);
        Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)
                .ifPresent(publisher -> publisher.publish(
                        new WebRequestFailure(context.method().name(), context.path(), exception)));
    }

    /**
     * \u83b7\u53d6\u8bf7\u6c42\u5ba2\u6237\u7aef\u5730\u5740\u3002
     * @param context Javalin \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u5ba2\u6237\u7aef\u5730\u5740
     */
    protected String getClientIp(Context context) {
        return WebKit.getClientIp(context);
    }

    /**
     * \u5224\u65ad\u8bf7\u6c42\u662f\u5426\u4e3a AJAX \u8bf7\u6c42\u3002
     * @param context Javalin \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u662f\u5426\u4e3a AJAX \u8bf7\u6c42
     */
    protected boolean isAjax(Context context) {
        return WebKit.isAjax(context);
    }
}
