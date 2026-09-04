package io.ddd4j.core.auth;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AuthPrincipal} (chained setters + field read/write).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class AuthPrincipalTest {

    @Test
    void chainedSetters_shouldReturnSameInstance() {
        AuthPrincipal principal = new AuthPrincipal();

        AuthPrincipal result = principal.setLoginId("u1").setUserId(100L);

        assertThat(result).isSameAs(principal);
    }

    @Test
    void setLoginId_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");

        assertThat(principal.getLoginId()).isEqualTo("u1");
    }

    @Test
    void setUserId_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setUserId(100L);

        assertThat(principal.getUserId()).isEqualTo(100L);
    }

    @Test
    void setRoles_shouldCarryRolePairs() {
        AuthPrincipal.RolePair role = new AuthPrincipal.RolePair()
                .setRoleId("r1").setRoleCode("admin").setRoleName("Admin");

        AuthPrincipal principal = new AuthPrincipal().setRoles(Collections.singletonList(role));

        assertThat(principal.getRoles()).hasSize(1);
        assertThat(principal.getRoles().get(0).getRoleCode()).isEqualTo("admin");
    }

    @Test
    void setPerms_shouldCarryPermissionSet() {
        AuthPrincipal principal = new AuthPrincipal().setPerms(Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("user:add", "user:del"))));

        assertThat(principal.getPerms()).containsExactlyInAnyOrder("user:add", "user:del");
    }

    @Test
    void setProfile_shouldCarryProfileMap() {
        AuthPrincipal principal = new AuthPrincipal()
                .setProfile(java.util.Collections.singletonMap("dept", "engineering"));

        assertThat(principal.getProfile()).containsEntry("dept", "engineering");
    }

    @Test
    void defaults_shouldBeSet() {
        AuthPrincipal principal = new AuthPrincipal();

        assertThat(principal.isBound()).isFalse();
        assertThat(principal.isInitial()).isFalse();
        assertThat(principal.isVerify()).isFalse();
    }

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

    @Test
    void rolePair_chainedSetters_shouldRoundTrip() {
        AuthPrincipal.RolePair role = new AuthPrincipal.RolePair()
                .setRoleId("r1").setRoleCode("rc").setRoleName("rn").setVerify(true);

        assertThat(role.getRoleId()).isEqualTo("r1");
        assertThat(role.getRoleCode()).isEqualTo("rc");
        assertThat(role.getRoleName()).isEqualTo("rn");
        assertThat(role.isVerify()).isTrue();
    }

    @Test
    void orgIdAndUserType_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setOrgId("org-1").setUserType("internal");

        assertThat(principal.getOrgId()).isEqualTo("org-1");
        assertThat(principal.getUserType()).isEqualTo("internal");
    }
}
