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

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 控制夹具验证共用断言的检错能力；不是 HTTP、原生认证引擎或平台验收。
 */
class SampleContractAssertionsTest {
    /**
     * 使用固定账号和权限数据运行指定引擎的鉴权合同断言。
     *
     * @param engine 鉴权引擎名称
     * @param driver 测试使用的鉴权驱动
     */
    private static void authenticate(String engine, AuthSampleDriver driver) {
        SampleContractAssertions.authentication(engine, driver, "alice", "fixture-password", "alice",
                "order:read", "order:delete", "orders-for-alice");
    }

    /**
     * 验证<code>authenticate(engine, driver)</code> 抛出 <code>AssertionError.class</code>，<code>failure.getMessage()</code> 等于 <code>"unsupported auth scenario name"</code>，<code>driver.requests</code> 等于 <code>Collections.emptyList()</code>。
     */
    @Test
    void rejectsUnknownAndMissingEngineBeforeCallingDriver() {
        for (String engine : Arrays.asList("unsupported", null)) {
            AuthDriver driver = new AuthDriver() {
                @Override
                public Optional<String> contextPrincipal() {
                    throw new AssertionError("driver touched before engine validation");
                }
            };
            AssertionError failure = assertThrows(AssertionError.class, () -> authenticate(engine, driver));
            assertEquals("unsupported auth scenario name", failure.getMessage());
            assertEquals(Collections.emptyList(), driver.requests);
        }
    }

    /**
     * 验证 Sa-Token 和 Shiro 的合同覆盖全部预期请求路径。
     */
    @Test
    void bothAuthNamesExerciseAllRequestBoundaries() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            AuthDriver driver = new AuthDriver();
            authenticate(engine, driver);
            assertEquals(Arrays.asList("login", "authenticated", "allowed", "denied", "failure", "logout",
                    "authenticated"), driver.requests);
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectAuthorizationBypass() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authorizedResource(String token, String permission) {
                    return "orders-for-alice";
                }
            }));
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectLogoutTokenReuse() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public void logout(String token) {
                    requests.add("logout");
                }
            }));
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectExceptionalContextLeak() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public void failingRequest(String token) {
                    context = "alice";
                    throw new IllegalStateException("injected business failure");
                }
            }));
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectSuccessfulRequestContextLeak() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authenticatedPrincipal(String token) {
                    String principal = super.authenticatedPrincipal(token);
                    context = principal;
                    return principal;
                }
            }));
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectWrongPrincipal() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authenticatedPrincipal(String token) {
                    return "another-user";
                }
            }));
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectDeniedRequestContextLeak() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authorizedResource(String token, String permission) {
                    try {
                        return super.authorizedResource(token, permission);
                    } catch (SecurityException denied) {
                        context = "alice";
                        throw denied;
                    }
                }
            }));
        }
    }

    /**
     * 验证被测调用 抛出 <code>AssertionError.class</code>。
     */
    @Test
    void bothAuthNamesDetectEmptyLoginToken() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String login(String username, String password) {
                    return " ";
                }
            }));
        }
    }

    /**
     * 模拟登录会话和请求边界的可替换鉴权测试驱动。
     */
    private static class AuthDriver implements AuthSampleDriver {
        /**
         * 驱动接收到的请求类别，按执行顺序保留。
         */
        final List<String> requests = new ArrayList<>();
        /**
         * 从测试令牌映射到登录用户名的会话记录。
         */
        final Map<String, String> sessions = new HashMap<>();
        /**
         * 驱动模拟请求期间绑定的主体用户名。
         */
        String context;

        /**
         * 以传入的凭据启动认证会话。
         *
         * <p>测试夹具会调用 <code>requests.add("login")</code>、<code>sessions.put("fixture-token", username)</code>。</p>
         *
         * @param username 已配置测试账号
         * @param password 已配置密码
         * @return 实际返回<code>"fixture-token"</code>
         */
        @Override
        public String login(String username, String password) {
            requests.add("login");
            sessions.put("fixture-token", username);
            return "fixture-token";
        }

        /**
         * 通过已认证请求探测服务端识别出的登录主体。
         *
         * <p>此实现更新<code>context = sessions.get(token)</code>。</p>
         *
         * @param token 令牌
         * @return 实际返回<code>context</code>
         */
        @Override
        public String authenticatedPrincipal(String token) {
            requests.add("authenticated");
            try {
                context = sessions.get(token);
                if (Objects.isNull(context)) {
                    throw new SecurityException("unauthenticated");
                }
                return context;
            } finally {
                context = null;
            }
        }

        /**
         * 访问当前鉴权契约中受权限约束的资源。
         *
         * <p>测试夹具会调用 <code>"order:read".equals(permission)</code>、<code>sessions.get(token)</code>。</p>
         *
         * @param token      令牌
         * @param permission 权限
         * @return 实际返回<code>"orders-for-" + context</code>
         */
        @Override
        public String authorizedResource(String token, String permission) {
            requests.add("order:read".equals(permission) ? "allowed" : "denied");
            try {
                context = sessions.get(token);
                if (Objects.isNull(context) || !"order:read".equals(permission)) {
                    throw new SecurityException("forbidden");
                }
                return "orders-for-" + context;
            } finally {
                context = null;
            }
        }

        /**
         * 结束由当前调用指定的认证会话。
         *
         * <p>测试夹具会调用 <code>requests.add("logout")</code>、<code>sessions.remove(token)</code>。</p>
         *
         * @param token 令牌；实际退出入口必须使该令牌失效
         */
        @Override
        public void logout(String token) {
            requests.add("logout");
            sessions.remove(token);
        }

        /**
         * 访问测试中指定会失败的请求路径。
         *
         * <p>测试夹具会调用 <code>requests.add("failure")</code>、<code>sessions.get(token)</code>。</p>
         *
         * @param token 令牌；调用已注入业务异常的受保护入口并保留 IllegalStateException
         */
        @Override
        public void failingRequest(String token) {
            requests.add("failure");
            try {
                context = sessions.get(token);
                throw new IllegalStateException("injected business failure");
            } finally {
                context = null;
            }
        }

        /**
         * 探测当前请求上下文里可读取的登录主体。
         *
         * <p>测试夹具会调用 <code>Optional.ofNullable(context)</code>。</p>
         *
         * @return 实际返回<code>Optional.ofNullable(context)</code>
         */
        @Override
        public Optional<String> contextPrincipal() {
            return Optional.ofNullable(context);
        }
    }
}
