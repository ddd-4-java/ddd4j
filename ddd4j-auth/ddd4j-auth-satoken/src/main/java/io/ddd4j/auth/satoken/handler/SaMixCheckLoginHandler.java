/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.ddd4j.auth.satoken.SaTempToken;
import io.ddd4j.auth.satoken.annotation.SaMixCheckLogin;
import io.ddd4j.auth.satoken.util.SaTempKit;
import io.ddd4j.core.constant.AuthConstants;

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

    /**
     * \u6309\u6ce8\u89e3\u8981\u6c42\u9a8c\u8bc1\u8bf7\u6c42\u4e34\u65f6\u4ee4\u724c\u6216\u5df2\u767b\u5f55\u4f1a\u8bdd\u3002
     * @param at \u58f0\u660e\u5f53\u524d\u5165\u53e3\u8ba4\u8bc1\u8981\u6c42\u7684\u6ce8\u89e3
     * @param element \u89e6\u53d1\u8ba4\u8bc1\u68c0\u67e5\u7684\u6ce8\u89e3\u5143\u7d20
     */
    @Override
    public void checkMethod(SaMixCheckLogin at, AnnotatedElement element) {
        // 获取前端请求提交的临时验证码
        String tempToken = SaHolder.getRequest().getParam(AuthConstants.PARAM_TEMP_TOKEN);
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

    /**
     * \u4ece\u4e34\u65f6 Token \u4e2d\u63d0\u53d6 Token \u8f7d\u8377\uff08JWT \u6269\u5c55\u4fe1\u606f\uff09\u3002
     * @param value \u4e34\u65f6 Token \u5bf9\u8c61
     * @return Token \u6269\u5c55\u6570\u636e Map
     *
     * @throws SaTokenException \u539f\u751f\u4ee4\u724c\u8f7d\u8377\u65e0\u6cd5\u8bfb\u53d6
     */
    public Map<String, Object> getTokenPayload(SaTempToken value) throws SaTokenException {
        return new HashMap<String, Object>() {{
            put(AuthConstants.JWT_AUTH_TYPE, value.getAuthType());
            put(AuthConstants.JWT_ISSUED_AT, DateTime.now());
            put(AuthConstants.JWT_SUBJECT, value.getLoginId());
        }};
    }

    /**
     * \u4ece\u4e34\u65f6 Token \u4e2d\u63d0\u53d6\u7ec8\u7aef\u8f7d\u8377\uff08\u8bbe\u5907\u4fe1\u606f\uff09\u3002
     * @param value \u4e34\u65f6 Token \u5bf9\u8c61
     * @return \u7ec8\u7aef\u6269\u5c55\u6570\u636e Map
     *
     * @throws SaTokenException \u5f53\u524d\u767b\u5f55\u7ec8\u7aef\u8f7d\u8377\u65e0\u6cd5\u8bfb\u53d6
     */
    public Map<String, Object> getTerminalPayload(SaTempToken value) throws SaTokenException {
        return new HashMap<String, Object>() {{
            put(AuthConstants.FIELD_APP_ID, value.getAppId());
            put(AuthConstants.FIELD_APP_CHANNEL, value.getAppChannel());
            put(AuthConstants.FIELD_APP_VERSION, value.getAppVersion());
            put(AuthConstants.FIELD_DEVICE_TYPE, value.getDeviceType());
            put(AuthConstants.FIELD_DEVICE_ID, value.getDeviceId());
        }};
    }

}
