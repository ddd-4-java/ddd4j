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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 控制夹具验证共用断言的检错能力；不是 HTTP、原生认证引擎或平台验收。
 */
class SampleContractAssertionsTest {
    /**
     * \u4f7f\u7528\u56fa\u5b9a\u8d26\u53f7\u548c\u6743\u9650\u6570\u636e\u8fd0\u884c\u6307\u5b9a\u5f15\u64ce\u7684\u9274\u6743\u5408\u540c\u65ad\u8a00\u3002
     * @param engine \u9274\u6743\u5f15\u64ce\u540d\u79f0
     * @param driver \u6d4b\u8bd5\u4f7f\u7528\u7684\u9274\u6743\u9a71\u52a8
     */
    private static void authenticate(String engine, AuthSampleDriver driver) {
        SampleContractAssertions.authentication(engine, driver, "alice", "fixture-password", "alice",
                "order:read", "order:delete", "orders-for-alice");
    }

    /** {@inheritDoc} */
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

    /** \u9a8c\u8bc1 Sa-Token \u548c Shiro \u7684\u5408\u540c\u8986\u76d6\u5168\u90e8\u9884\u671f\u8bf7\u6c42\u8def\u5f84\u3002 */
    @Test
    void bothAuthNamesExerciseAllRequestBoundaries() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            AuthDriver driver = new AuthDriver();
            authenticate(engine, driver);
            assertEquals(Arrays.asList("login", "authenticated", "allowed", "denied", "failure", "logout",
                    "authenticated"), driver.requests);
        }
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** \u6a21\u62df\u767b\u5f55\u4f1a\u8bdd\u548c\u8bf7\u6c42\u8fb9\u754c\u7684\u53ef\u66ff\u6362\u9274\u6743\u6d4b\u8bd5\u9a71\u52a8\u3002 */
    private static class AuthDriver implements AuthSampleDriver {
        /** \u9a71\u52a8\u63a5\u6536\u5230\u7684\u8bf7\u6c42\u7c7b\u522b\uff0c\u6309\u6267\u884c\u987a\u5e8f\u4fdd\u7559\u3002 */
        final List<String> requests = new ArrayList<>();
        /** \u4ece\u6d4b\u8bd5\u4ee4\u724c\u6620\u5c04\u5230\u767b\u5f55\u7528\u6237\u540d\u7684\u4f1a\u8bdd\u8bb0\u5f55\u3002 */
        final Map<String, String> sessions = new HashMap<>();
        /** \u9a71\u52a8\u6a21\u62df\u8bf7\u6c42\u671f\u95f4\u7ed1\u5b9a\u7684\u4e3b\u4f53\u7528\u6237\u540d\u3002 */
        String context;

        /** {@inheritDoc} */
        @Override
        public String login(String username, String password) {
            requests.add("login");
            sessions.put("fixture-token", username);
            return "fixture-token";
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public void logout(String token) {
            requests.add("logout");
            sessions.remove(token);
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public Optional<String> contextPrincipal() {
            return Optional.ofNullable(context);
        }
    }
}
