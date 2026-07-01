package io.ddd4j.auth.satoken.handler;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.error.SaErrorCode;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.date.DateTime;
import io.ddd4j.auth.satoken.SaConstants;
import io.ddd4j.auth.satoken.SaTempToken;
import io.ddd4j.auth.satoken.annotation.SaMixCheckLogin;
import io.ddd4j.auth.satoken.util.SaTempKit;

import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 注解 SaMixCheckLogin 的处理器
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SaMixCheckLoginHandler implements SaAnnotationHandlerInterface<SaMixCheckLogin> {


    /**
     * 指定这个处理器要处理哪个注解
     *
     * @return 注解类
     */
    @Override
    public Class<SaMixCheckLogin> getHandlerAnnotationClass() {
        return SaMixCheckLogin.class;
    }

    @Override
    public void checkMethod(SaMixCheckLogin at, AnnotatedElement element) {
        // 获取前端请求提交的临时验证码
        String tempToken = SaHolder.getRequest().getParam(SaConstants.PARAM_TEMP_TOKEN);
        // 如果存在，则校验
        if (Objects.nonNull(tempToken) && io.ddd4j.kit.lang.StrKit.isNotBlank(tempToken)) {
            try {
                // 获取指定 业务标识、指定 Token 的剩余有效期，单位：秒
                long timeout = SaTempKit.getTimeout(tempToken);
                // 返回值 -1 代表永久，-2 代表token无效
                if (timeout == -2) {
                    // 校验不通过，则抛出异常
                    throw new SaTokenException(SaErrorCode.CODE_11013, "Token已过期，未通过校验");
                }
                SaTempToken saTempToken = SaTempKit.parseToken(tempToken);
                if (Objects.isNull(saTempToken)) {
                    throw new SaTokenException(SaErrorCode.CODE_11012, "无效的Token，未通过校验");
                }
                // 检查登录时的账号id值是否为空
                if (Objects.isNull(saTempToken.getLoginId())
                        || !io.ddd4j.kit.lang.StrKit.isNotBlank(saTempToken.getLoginId())) {
                    throw new SaTokenException(SaErrorCode.CODE_11002, "登录时的账号id值为空");
                }
                // 判断临时Token是否是需要登录的，且不是一次性使用的；则进行登录操作
                if (at.login() && !at.throwaway()) {
                    Map<String, Object> tokenExtraData = this.getTokenPayload(saTempToken);
                    Map<String, Object> terminalExtraData = this.getTerminalPayload(saTempToken);
                    StpUtil.login(saTempToken.getLoginId(), new SaLoginParameter()
                            // 从缓存中获取，此次登录的客户端设备类型, 用于完成 [同端互斥登录] 功能
                            .setDeviceType(saTempToken.getDeviceType())
                            // 此次登录的客户端设备ID, 登录成功后该设备将标记为可信任设备
                            .setDeviceId(saTempToken.getDeviceId())
                            // 记录在 Token 上的扩展参数（只在 jwt 模式下生效）
                            .setExtraData(tokenExtraData)
                            // 本次登录挂载到 SaTerminalInfo 的自定义扩展数据
                            .setTerminalExtraData(terminalExtraData)
                            // 保持有效期与 临时Token 一样
                            .setTimeout(timeout)
                    );
                }
            } finally {
                // 判断临时Token是否是一次性使用的；用完即弃的，如果是，则删除
                if (at.throwaway()) {
                    SaTempKit.deleteToken(tempToken);
                }
            }
        } else {
            // 执行 @SaCheckLogin 注解的逻辑
            StpLogic stpLogic = SaManager.getStpLogic(at.type(), false);
            stpLogic.checkLogin();
        }
        // 校验通过，什么也不做
    }

    public Map<String, Object> getTokenPayload(SaTempToken value) throws SaTokenException {
        return new HashMap<String, Object>() {{
            put(SaConstants.PAYLOAD_AUTH_TYPE, value.getAuthType());
            put(SaConstants.PAYLOAD_ISSUED_AT, DateTime.now());
            put(SaConstants.PAYLOAD_SUBJECT, value.getLoginId());
        }};
    }

    public Map<String, Object> getTerminalPayload(SaTempToken value) throws SaTokenException {
        return new HashMap<String, Object>() {{
            put(SaConstants.FIELD_APP_ID, value.getAppId());
            put(SaConstants.FIELD_APP_CHANNEL, value.getAppChannel());
            put(SaConstants.FIELD_APP_VERSION, value.getAppVersion());
            put(SaConstants.FIELD_DEVICE_TYPE, value.getDeviceType());
            put(SaConstants.FIELD_DEVICE_ID, value.getDeviceId());
        }};
    }

}
