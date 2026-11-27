package io.ddd4j.spring.i18n;

import io.ddd4j.core.i18n.I18nProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Spring 实现的国际化提供者
 * <p>
 * 基于 Spring MessageSource 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class SpringI18nProvider implements I18nProvider {

    /**
     * Spring 嵌套消息源
     */
    private final NestedMessageSource messageSource;

    /**
     * \u521b\u5efa\u4f7f\u7528\u5d4c\u5957\u6d88\u606f\u6e90\u7684\u56fd\u9645\u5316\u63d0\u4f9b\u8005\u3002
     * @param messageSource \u5d4c\u5957\u6d88\u606f\u6e90
     */
    public SpringI18nProvider(NestedMessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * \u4f7f\u7528\u5f53\u524d\u7ebf\u7a0b\u7684 Locale \u89e3\u6790\u56fd\u9645\u5316\u6d88\u606f\u3002
     * @param key \u6d88\u606f\u4ee3\u7801
     * @param args \u6d88\u606f\u5360\u4f4d\u7b26\u53c2\u6570
     * @return \u5f53\u524d Locale \u5bf9\u5e94\u7684\u6d88\u606f\u6587\u672c
     */
    @Override
    public String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, locale);
    }
}
