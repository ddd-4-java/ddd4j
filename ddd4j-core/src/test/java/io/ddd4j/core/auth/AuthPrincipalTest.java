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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AuthPrincipal} (chained setters + field read/write).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class AuthPrincipalTest {

    /**
     * 验证<code>result</code> 指向同一实例 <code>principal</code>。
     */
    @Test
    void chainedSetters_shouldReturnSameInstance() {
        AuthPrincipal principal = new AuthPrincipal();

        AuthPrincipal result = principal.setLoginId("u1").setUserId(100L);

        assertThat(result).isSameAs(principal);
    }

    /**
     * 验证<code>principal.getLoginId()</code> 等于 <code>"u1"</code>。
     */
    @Test
    void setLoginId_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");

        assertThat(principal.getLoginId()).isEqualTo("u1");
    }

    /**
     * 验证<code>principal.getUserId()</code> 等于 <code>100L</code>。
     */
    @Test
    void setUserId_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setUserId(100L);

        assertThat(principal.getUserId()).isEqualTo(100L);
    }

    /**
     * 验证<code>principal.getRoles()</code> 的大小为 <code>1</code>，<code>principal.getRoles().get(0).getRoleCode()</code> 等于 <code>"admin"</code>。
     */
    @Test
    void setRoles_shouldCarryRolePairs() {
        AuthPrincipal.RolePair role = new AuthPrincipal.RolePair()
                .setRoleId("r1").setRoleCode("admin").setRoleName("Admin");

        AuthPrincipal principal = new AuthPrincipal().setRoles(Collections.singletonList(role));

        assertThat(principal.getRoles()).hasSize(1);
        assertThat(principal.getRoles().get(0).getRoleCode()).isEqualTo("admin");
    }

    /**
     * 验证<code>principal.getPerms()</code> 仅包含 <code>"user:add"</code>、<code>"user:del"</code>。
     */
    @Test
    void setPerms_shouldCarryPermissionSet() {
        AuthPrincipal principal = new AuthPrincipal().setPerms(Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("user:add", "user:del"))));

        assertThat(principal.getPerms()).containsExactlyInAnyOrder("user:add", "user:del");
    }

    /**
     * 验证<code>principal.getProfile()</code> 含对应条目 <code>"dept"</code>、<code>"engineering"</code>。
     */
    @Test
    void setProfile_shouldCarryProfileMap() {
        AuthPrincipal principal = new AuthPrincipal()
                .setProfile(java.util.Collections.singletonMap("dept", "engineering"));

        assertThat(principal.getProfile()).containsEntry("dept", "engineering");
    }

    /**
     * 验证<code>principal.isBound()</code>为假，<code>principal.isInitial()</code>为假，<code>principal.isVerify()</code>为假。
     */
    @Test
    void defaults_shouldBeSet() {
        AuthPrincipal principal = new AuthPrincipal();

        assertThat(principal.isBound()).isFalse();
        assertThat(principal.isInitial()).isFalse();
        assertThat(principal.isVerify()).isFalse();
    }

    /**
     * 验证<code>principal.getAppId()</code> 等于 <code>"app-1"</code>，<code>principal.getAppChannel()</code> 等于 <code>"web"</code>，<code>principal.getUserAgent()</code> 等于 <code>"ua"</code>。
     */
    @Test
    void requestSourceFields_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal()
                .setAppId("app-1")
                .setAppChannel("web")
                .setIpAddress("10.0.0.1")
                .setDeviceType("pc")
                .setUserAgent("ua");

        assertThat(principal.getAppId()).isEqualTo("app-1");
        assertThat(principal.getAppChannel()).isEqualTo("web");
        assertThat(principal.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(principal.getDeviceType()).isEqualTo("pc");
        assertThat(principal.getUserAgent()).isEqualTo("ua");
    }

    /**
     * 验证<code>role.getRoleId()</code> 等于 <code>"r1"</code>，<code>role.getRoleCode()</code> 等于 <code>"rc"</code>，<code>role.isVerify()</code>为真。
     */
    @Test
    void rolePair_chainedSetters_shouldRoundTrip() {
        AuthPrincipal.RolePair role = new AuthPrincipal.RolePair()
                .setRoleId("r1").setRoleCode("rc").setRoleName("rn").setVerify(true);

        assertThat(role.getRoleId()).isEqualTo("r1");
        assertThat(role.getRoleCode()).isEqualTo("rc");
        assertThat(role.getRoleName()).isEqualTo("rn");
        assertThat(role.isVerify()).isTrue();
    }

    /**
     * 验证<code>principal.getOrgId()</code> 等于 <code>"org-1"</code>，<code>principal.getUserType()</code> 等于 <code>"internal"</code>。
     */
    @Test
    void orgIdAndUserType_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setOrgId("org-1").setUserType("internal");

        assertThat(principal.getOrgId()).isEqualTo("org-1");
        assertThat(principal.getUserType()).isEqualTo("internal");
    }
}
