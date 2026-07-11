package io.ddd4j.extension.jackson.annotation;

import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

/**
 * 数据脱敏策略。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 11 :25
 */
public enum SensitiveStrategy {

    /**
     * 不进行脱敏，保留原始值。
     */
    NONE(Function.identity()),
    /**
     * 通用脱敏，保留首尾各一个字符。
     */
    DEFAULT(value -> maskBetween(value, 1, 1)),
    /**
     * 用户名脱敏，保留首尾各一个字符。
     */
    USERNAME(value -> maskBetween(value, 1, 1)),
    /**
     * 中文姓名脱敏，仅保留第一个字符。
     */
    CHINESE_NAME(value -> maskBetween(value, 1, 0)),
    /**
     * 身份证号码脱敏，保留前三位和后四位。
     */
    ID_CARD(value -> maskBetween(value, 3, 4)),
    /**
     * 手机号码脱敏，保留前三位和后四位，例如：185****1653。
     */
    PHONE(value -> maskBetween(value, 3, 4)),
    /**
     * 固定电话号码脱敏，保留前两位和后四位。
     */
    FIXED_PHONE(value -> maskBetween(value, 2, 4)),
    /**
     * 地址脱敏，保留前六个字符。
     */
    ADDRESS(value -> maskBetween(value, 6, 0)),
    /**
     * 电子邮箱脱敏，仅保留邮箱名前缀的首字符及完整域名，例如：r*****@qq.com。
     */
    EMAIL(SensitiveStrategy::maskEmail),
    /**
     * 银行卡号脱敏，保留前四位和后四位。
     */
    BANK_CARD(value -> maskBetween(value, 4, 4)),
    /**
     * 银行联行号脱敏，保留前四位。
     */
    CNAPS_CODE(value -> maskBetween(value, 4, 0)),
    /**
     * 支付签约协议号脱敏，保留前六位和后六位。
     */
    PAY_SIGN_NO(value -> maskBetween(value, 6, 6)),

    ;

    /**
     * 脱敏处理函数
     */
    private final Function<String, String> desensitizer;

    /**
     * 构造函数
     *
     * @param desensitizer 脱敏处理函数
     */
    SensitiveStrategy(Function<String, String> desensitizer) {
        this.desensitizer = desensitizer;
    }

    /**
     * 对指定字符串执行当前脱敏策略。
     *
     * @param value 原始字符串
     * @return 脱敏后的字符串；原始值为空时直接返回
     */
    public String mask(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        return desensitizer.apply(value);
    }

    /**
     * 对电子邮箱地址执行脱敏。
     *
     * @param value 原始电子邮箱地址
     * @return 脱敏后的电子邮箱地址
     */
    private static String maskEmail(String value) {
        int atIndex = StringUtils.indexOf(value, '@');
        if (atIndex <= 1) {
            return value;
        }
        return StringUtils.left(value, 1)
                + StringUtils.repeat('*', atIndex - 1)
                + StringUtils.substring(value, atIndex);
    }

    /**
     * 对字符串中间部分执行脱敏，并保留指定长度的前缀和后缀。
     *
     * @param value         原始字符串
     * @param visiblePrefix 保留的前缀长度
     * @param visibleSuffix 保留的后缀长度
     * @return 脱敏后的字符串
     */
    private static String maskBetween(String value, int visiblePrefix, int visibleSuffix) {
        int length = StringUtils.length(value);
        if (length <= visiblePrefix + visibleSuffix) {
            return value;
        }
        return StringUtils.left(value, visiblePrefix)
                + StringUtils.repeat('*', length - visiblePrefix - visibleSuffix)
                + StringUtils.right(value, visibleSuffix);
    }
}
