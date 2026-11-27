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
     * \u58f0\u660e\u7531\u5f53\u524d\u5904\u7406\u5668\u89e3\u91ca\u7684\u5185\u90e8\u8ba4\u8bc1\u6ce8\u89e3\u3002
     * @return \u5185\u90e8 API \u6821\u9a8c\u6ce8\u89e3\u7684\u7c7b\u578b
     */
    @Override
    public Class<SaInternalCheck> getHandlerAnnotationClass() {
        return SaInternalCheck.class;
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u7684 API \u5bc6\u94a5\u5e76\u6309\u6ce8\u89e3\u8303\u56f4\u6267\u884c\u5185\u90e8\u8ba4\u8bc1\u3002
     * @param at \u58f0\u660e\u5f53\u524d\u5165\u53e3\u8ba4\u8bc1\u8981\u6c42\u7684\u6ce8\u89e3
     * @param element \u89e6\u53d1\u8ba4\u8bc1\u68c0\u67e5\u7684\u6ce8\u89e3\u5143\u7d20
     * @throws SaTokenException \u8bf7\u6c42\u7f3a\u5c11\u5bc6\u94a5\u6216\u672a\u901a\u8fc7\u5185\u90e8\u68c0\u67e5
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
