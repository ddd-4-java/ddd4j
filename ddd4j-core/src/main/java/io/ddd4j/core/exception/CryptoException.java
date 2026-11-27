package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;

/**
 * 加解密操作异常。
 * <p>
 * 当加密、解密、签名验证等密码学操作失败时抛出。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CryptoException extends BizRuntimeException {

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public CryptoException(Integer code, String message) {
        super(code, message);
    }

    /**
     * \u6309\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public CryptoException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    /**
     * \u6309\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public CryptoException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * \u6309\u9884\u5b9a\u4e49\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u952e\u521b\u5efa\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public CryptoException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public CryptoException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * \u6309\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param defMsg \u8d44\u6e90\u7f3a\u5931\u65f6\u7684\u9ed8\u8ba4\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public CryptoException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    /**
     * \u6309\u81ea\u5b9a\u4e49\u4e1a\u52a1\u7801\u521b\u5efa\u52a0\u5bc6\u5904\u7406\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     */
    public CryptoException(CustomApiCode code) {
        super(code);
    }

}
