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
package io.ddd4j.sample.order.testkit;

import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;

/**
 * 各鉴权样例共用的认证、授权与请求作用域断言。
 */
public final class SampleContractAssertions {
    /** \u9650\u5236\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private SampleContractAssertions() {
    }

    /**
     * 对 Sa-Token 或 Shiro 执行登录、认证、授权允许/拒绝、业务异常、退出失效及请求清理断言。
     * 测试账号应只具有 allowedPermission；平台适配器保留原生 HTTP 格式并从真实响应提取资源。
     *
     * @param engine            satoken 或 shiro
     * @param driver            认证驱动
     * @param username          测试账号
     * @param password          测试密码
     * @param expectedPrincipal 实际受保护入口应返回的主体标识，可与登录账号不同
     * @param allowedPermission 允许权限
     * @param deniedPermission  拒绝权限
     * @param expectedResource  允许访问时的业务资源
     */
    public static void authentication(String engine, AuthSampleDriver driver, String username,
                                      String password, String expectedPrincipal, String allowedPermission, String deniedPermission, String expectedResource) {
        check("satoken".equals(engine) || "shiro".equals(engine), "unsupported auth scenario name");
        clean(driver, engine + " initial context");
        String token = driver.login(username, password);
        check(StrKit.isNotBlank(token), engine + " login must return a token");
        clean(driver, engine + " login cleanup");
        check(Objects.equals(expectedPrincipal, driver.authenticatedPrincipal(token)), engine + " authenticated principal");
        clean(driver, engine + " authenticated request cleanup");
        check(Objects.equals(expectedResource, driver.authorizedResource(token, allowedPermission)),
                engine + " allowed resource");
        clean(driver, engine + " allowed request cleanup");
        failure(SecurityException.class, () -> driver.authorizedResource(token, deniedPermission),
                engine + " denied permission must not return a resource");
        clean(driver, engine + " denied request cleanup");
        failure(IllegalStateException.class, () -> driver.failingRequest(token),
                engine + " injected business failure must propagate");
        clean(driver, engine + " exceptional request cleanup");
        driver.logout(token);
        clean(driver, engine + " logout cleanup");
        failure(SecurityException.class, () -> driver.authenticatedPrincipal(token),
                engine + " logged-out token must be rejected");
        clean(driver, engine + " invalid token request cleanup");
    }

    /**
     * \u65ad\u8a00\u4e00\u6b21\u8bf7\u6c42\u7ed3\u675f\u540e\u6ca1\u6709\u6b8b\u7559\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param driver \u53ef\u8bfb\u53d6\u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53\u7684\u793a\u4f8b\u9a71\u52a8
     * @param message \u65ad\u8a00\u5931\u8d25\u65f6\u7684\u63d0\u793a
     */
    private static void clean(AuthSampleDriver driver, String message) {
        check(!driver.contextPrincipal().isPresent(), message);
    }

    /**
     * \u65ad\u8a00\u6267\u884c\u64cd\u4f5c\u65f6\u4f1a\u629b\u51fa\u6307\u5b9a\u7c7b\u578b\u7684\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param type \u671f\u671b\u7684\u5f02\u5e38\u7c7b\u578b
     * @param operation \u88ab\u9a8c\u8bc1\u7684\u64cd\u4f5c
     * @param message \u65ad\u8a00\u5931\u8d25\u65f6\u7684\u63d0\u793a
     */
    private static void failure(Class<? extends RuntimeException> type, Runnable operation, String message) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            if (type.isInstance(exception)) {
                return;
            }
            throw new AssertionError(message + ": unexpected exception", exception);
        }
        throw new AssertionError(message);
    }

    /**
     * \u5728\u6761\u4ef6\u4e0d\u6ee1\u8db3\u65f6\u629b\u51fa\u5e26\u7ed9\u5b9a\u6d88\u606f\u7684\u65ad\u8a00\u5931\u8d25\u3002
     * @param condition \u5f85\u9a8c\u8bc1\u7684\u6761\u4ef6
     * @param message \u65ad\u8a00\u5931\u8d25\u65f6\u7684\u63d0\u793a
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
