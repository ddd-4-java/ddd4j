package io.ddd4j.auth.satoken.config;

/**
 * Sa-Token 认证凭证模式。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public enum SaTokenAuthenticationMode {

    /**
     * 服务端 Session Token。
     */
    SESSION,

    /**
     * Sa-Token JWT Simple：JWT 签名配合服务端映射实现撤销。
     */
    JWT_SIMPLE
}
