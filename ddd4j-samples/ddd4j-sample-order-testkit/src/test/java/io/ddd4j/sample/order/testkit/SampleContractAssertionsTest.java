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
    private static void authenticate(String engine, AuthSampleDriver driver) {
        SampleContractAssertions.authentication(engine, driver, "alice", "fixture-password", "alice",
                "order:read", "order:delete", "orders-for-alice");
    }

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

    @Test
    void bothAuthNamesExerciseAllRequestBoundaries() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            AuthDriver driver = new AuthDriver();
            authenticate(engine, driver);
            assertEquals(Arrays.asList("login", "authenticated", "allowed", "denied", "failure", "logout",
                    "authenticated"), driver.requests);
        }
    }

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

    private static class AuthDriver implements AuthSampleDriver {
        final List<String> requests = new ArrayList<>();
        final Map<String, String> sessions = new HashMap<>();
        String context;

        @Override
        public String login(String username, String password) {
            requests.add("login");
            sessions.put("fixture-token", username);
            return "fixture-token";
        }

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

        @Override
        public void logout(String token) {
            requests.add("logout");
            sessions.remove(token);
        }

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

        @Override
        public Optional<String> contextPrincipal() {
            return Optional.ofNullable(context);
        }
    }
}
