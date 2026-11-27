package io.ddd4j.auth.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;

import java.util.Objects;

/**
 * 将 ddd4j 安全契约安装到 Sa-Token 全局运行时。
 *
 * <p>必须在应用接收请求前调用一次。配置校验先于任何 Sa-Token 全局状态修改执行，
 * 因此错误配置会 fail-fast，不会留下半初始化认证运行时。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class SaTokenSecurityConfigurer {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>SaTokenSecurityConfigurer</code> \u5b9e\u4f8b\u3002 */
    private SaTokenSecurityConfigurer() {
    }

    /**
     * \u6821\u9a8c\u5e76\u5b89\u88c5\u5b89\u5168\u914d\u7f6e\uff0c\u8fd4\u56de\u5f53\u524d\u9ed8\u8ba4\u8d26\u53f7\u4f53\u7cfb\u7684 StpLogic\u3002
     *
     * @param properties Sa-Token \u5b89\u5168\u914d\u7f6e
     * @return \u5f53\u524d\u9ed8\u8ba4\u8d26\u53f7\u4f53\u7cfb\u7684 StpLogic
     */
    public static StpLogic configure(SaTokenSecurityProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        properties.validate();

        SaTokenConfig config = Objects.requireNonNull(SaManager.getConfig(), "Sa-Token configuration must not be null");
        config.setTokenName(properties.tokenName());
        config.setTimeout(properties.getTokenTtlSeconds());
        config.setActiveTimeout(properties.getActiveTimeoutSeconds());
        // JWT Simple 的撤销依赖 token session/mapping，禁止关闭该检查。
        config.setTokenSessionCheckLogin(true);

        StpLogic logic = properties.getAuthenticationMode() == SaTokenAuthenticationMode.JWT_SIMPLE
                ? new Ddd4jStpLogicJwtForSimple(properties)
                : new StpLogic(properties.getLoginType());
        StpUtil.setStpLogic(logic);
        return logic;
    }
}
