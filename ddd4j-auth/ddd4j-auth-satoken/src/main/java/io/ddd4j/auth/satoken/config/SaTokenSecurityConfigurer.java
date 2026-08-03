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

    private SaTokenSecurityConfigurer() {
    }

    /**
     * 校验并安装安全配置，返回当前默认账号体系的 StpLogic。
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
