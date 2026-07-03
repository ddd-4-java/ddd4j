package io.ddd4j.extension.jackson.annotation;

import java.util.function.Function;

/**
 * 脱敏策略.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 11 :25
 */
public enum SensitiveStrategy {

    /**
     * Username sensitive strategy.
     */
    USERNAME(s -> s.replaceAll("(\\S)\\S(\\S*)", "$1*$2")),
    /**
     * Id card sensitive type.
     */
    ID_CARD(s -> s.replaceAll("(\\d{4})\\d{10}(\\w{4})", "$1****$2")),
    /**
     * 手机号, 185****1653
     */
    PHONE(s -> s.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2")),
    /**
     * Address sensitive type.
     */
    ADDRESS(s -> s.replaceAll("(\\S{3})\\S{2}(\\S*)\\S{2}", "$1****$2****")),
    /**
     * 电子邮件, r*****o@qq.com
     */
    EMAIL(s -> s.replaceAll("(\\S)\\S*(@\\S*)", "$1****$2")),

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
     * 获取脱敏处理函数
     *
     * @return 脱敏处理函数
     */
    public Function<String, String> desensitizer() {
        return desensitizer;
    }
}