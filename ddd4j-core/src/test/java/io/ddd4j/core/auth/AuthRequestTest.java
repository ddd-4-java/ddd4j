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
package io.ddd4j.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AuthRequest}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class AuthRequestTest {

    @Test
    void of_shouldCreateWithLoginId() {
        AuthRequest request = AuthRequest.of("u1");

        assertThat(request.getLoginId()).isEqualTo("u1");
    }

    @Test
    void constructor_shouldSetLoginId() {
        AuthRequest request = new AuthRequest(42L);

        assertThat(request.getLoginId()).isEqualTo(42L);
    }

    @Test
    void getLoginIdAsString_shouldStringifyLoginId() {
        AuthRequest request = AuthRequest.of(123L);

        assertThat(request.getLoginIdAsString()).isEqualTo("123");
    }

    @Test
    void getLoginIdAsString_shouldReturnNullWhenAbsent() {
        AuthRequest request = new AuthRequest();

        assertThat(request.getLoginIdAsString()).isNull();
    }

    @Test
    void setTimeout_shouldBeChainableAndForwardToSessionConfig() {
        AuthRequest request = AuthRequest.of("u1");

        AuthRequest result = request.setTimeout(600L);

        assertThat(result).isSameAs(request);
        assertThat(request.getTimeout()).isEqualTo(600L);
    }

    @Test
    void sessionConfigRoundTripDistinguishesInheritedAndPermanentTtl() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AuthSessionConfig inherited = new AuthSessionConfig();
        AuthSessionConfig permanent = new AuthSessionConfig().setTimeout(-1);

        assertThat(mapper.readValue(mapper.writeValueAsString(inherited), AuthSessionConfig.class)
                .hasTimeoutOverride()).isFalse();
        assertThat(mapper.readValue(mapper.writeValueAsString(permanent), AuthSessionConfig.class)
                .hasTimeoutOverride()).isTrue();
    }

    @Test
    void requestOmitsInheritedTimeoutAndPreservesExplicitFlatCompatibility() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.readTree(mapper.writeValueAsString(AuthRequest.of("u1"))).has("timeout"))
                .isFalse();
        assertThat(mapper.readTree(mapper.writeValueAsString(AuthRequest.of("u1").setTimeout(-1)))
                .get("timeout").asLong()).isEqualTo(-1);
        AuthRequest legacy = mapper.readValue("{\"loginId\":\"u1\",\"timeout\":-1}", AuthRequest.class);
        assertThat(legacy.getSessionConfig().hasTimeoutOverride()).isTrue();
        assertThat(legacy.getTimeout()).isEqualTo(-1);
    }

    @Test
    void strictRequestRoundTripPreservesLoginIdAndTimeoutPresence() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode inherited = mapper.valueToTree(AuthRequest.of("actual"));
        JsonNode permanent = mapper.valueToTree(AuthRequest.of("actual").setTimeout(-1));

        assertThat(inherited.path("loginIdAsString").asText()).isEqualTo("actual");
        assertThat(inherited.has("timeout")).isFalse();
        assertThat(permanent.path("timeout").asLong()).isEqualTo(-1);

        AuthRequest inheritedRoundTrip = mapper.treeToValue(inherited, AuthRequest.class);
        AuthRequest permanentRoundTrip = mapper.treeToValue(permanent, AuthRequest.class);
        assertThat(inheritedRoundTrip.getLoginId()).isEqualTo("actual");
        assertThat(permanentRoundTrip.getLoginId()).isEqualTo("actual");
        assertThat(inheritedRoundTrip.getSessionConfig().hasTimeoutOverride()).isFalse();
        assertThat(permanentRoundTrip.getSessionConfig().hasTimeoutOverride()).isTrue();
        assertThat(permanentRoundTrip.getTimeout()).isEqualTo(-1);
    }

    @Test
    void strictRequestTreatsComputedLoginIdAsReadOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AuthRequest actual = mapper.readValue(
                "{\"loginId\":\"actual\",\"loginIdAsString\":\"supplied\"}", AuthRequest.class);
        AuthRequest computedOnly = mapper.readValue("{\"loginIdAsString\":\"supplied\"}", AuthRequest.class);

        assertThat(actual.getLoginId()).isEqualTo("actual");
        assertThat(actual.getLoginIdAsString()).isEqualTo("actual");
        assertThat(computedOnly.getLoginId()).isNull();
        assertThatThrownBy(() -> mapper.readValue("{\"unmapped\":true}", AuthRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void setDeviceType_shouldForwardToSessionConfig() {
        AuthRequest request = AuthRequest.of("u1").setDeviceType("mobile");

        assertThat(request.getDeviceType()).isEqualTo("mobile");
    }

    @Test
    void setPrincipal_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");
        AuthRequest request = AuthRequest.of("u1").setPrincipal(principal);

        assertThat(request.getPrincipal()).isSameAs(principal);
    }

    @Test
    void extra_shouldAddEntryAndChain() {
        AuthRequest request = AuthRequest.of("u1");

        AuthRequest result = request.extra("k", "v");

        assertThat(result).isSameAs(request);
        assertThat(request.getExtra()).containsEntry("k", "v");
    }

    @Test
    void extra_shouldRejectNullKey() {
        AuthRequest request = AuthRequest.of("u1");

        assertThatThrownBy(() -> request.extra(null, "v"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equals_shouldBeBasedOnLoginId() {
        AuthRequest a = AuthRequest.of("u1");
        AuthRequest b = AuthRequest.of("u1");
        AuthRequest c = AuthRequest.of("u2");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void toString_shouldContainLoginIdAndRealm() {
        AuthRequest request = AuthRequest.of("u1").setRealm("admin");

        String s = request.toString();

        assertThat(s).contains("u1").contains("admin");
    }

    @Test
    void realm_shouldRoundTrip() {
        AuthRequest request = AuthRequest.of("u1").setRealm("user");

        assertThat(request.getRealm()).isEqualTo("user");
    }
}
