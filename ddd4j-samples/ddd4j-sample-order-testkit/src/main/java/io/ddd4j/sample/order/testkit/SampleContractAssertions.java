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
    /**
     * 限制工具类实例化。
     */
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
     * 断言一次请求结束后没有残留的认证主体。
     *
     * @param driver  可读取当前认证主体的示例驱动
     * @param message 断言失败时的提示
     */
    private static void clean(AuthSampleDriver driver, String message) {
        check(!driver.contextPrincipal().isPresent(), message);
    }

    /**
     * 断言执行操作时会抛出指定类型的运行时异常。
     *
     * @param type      期望的异常类型
     * @param operation 被验证的操作
     * @param message   断言失败时的提示
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
     * 在条件不满足时抛出带给定消息的断言失败。
     *
     * @param condition 待验证的条件
     * @param message   断言失败时的提示
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
