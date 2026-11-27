package io.ddd4j.core.auth;

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

    /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void chainedSetters_shouldReturnSameInstance() {
        AuthPrincipal principal = new AuthPrincipal();

        AuthPrincipal result = principal.setLoginId("u1").setUserId(100L);

        assertThat(result).isSameAs(principal);
    }

    /** \u9a8c\u8bc1<code>principal.getLoginId()</code> \u7b49\u4e8e <code>"u1"</code>\u3002 */
    @Test
    void setLoginId_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");

        assertThat(principal.getLoginId()).isEqualTo("u1");
    }

    /** \u9a8c\u8bc1<code>principal.getUserId()</code> \u7b49\u4e8e <code>100L</code>\u3002 */
    @Test
    void setUserId_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setUserId(100L);

        assertThat(principal.getUserId()).isEqualTo(100L);
    }

    /** \u9a8c\u8bc1<code>principal.getRoles()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>principal.getRoles().get(0).getRoleCode()</code> \u7b49\u4e8e <code>"admin"</code>\u3002 */
    @Test
    void setRoles_shouldCarryRolePairs() {
        AuthPrincipal.RolePair role = new AuthPrincipal.RolePair()
                .setRoleId("r1").setRoleCode("admin").setRoleName("Admin");

        AuthPrincipal principal = new AuthPrincipal().setRoles(List.of(role));

        assertThat(principal.getRoles()).hasSize(1);
        assertThat(principal.getRoles().get(0).getRoleCode()).isEqualTo("admin");
    }

    /** \u9a8c\u8bc1<code>principal.getPerms()</code> \u4ec5\u5305\u542b <code>"user:add"</code>\u3001<code>"user:del"</code>\u3002 */
    @Test
    void setPerms_shouldCarryPermissionSet() {
        AuthPrincipal principal = new AuthPrincipal().setPerms(Set.of("user:add", "user:del"));

        assertThat(principal.getPerms()).containsExactlyInAnyOrder("user:add", "user:del");
    }

    /** \u9a8c\u8bc1<code>principal.getProfile()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"dept"</code>\u3001<code>"engineering"</code>\u3002 */
    @Test
    void setProfile_shouldCarryProfileMap() {
        AuthPrincipal principal = new AuthPrincipal()
                .setProfile(java.util.Map.of("dept", "engineering"));

        assertThat(principal.getProfile()).containsEntry("dept", "engineering");
    }

    /** \u9a8c\u8bc1<code>principal.isBound()</code>\u4e3a\u5047\uff0c<code>principal.isInitial()</code>\u4e3a\u5047\uff0c<code>principal.isVerify()</code>\u4e3a\u5047\u3002 */
    @Test
    void defaults_shouldBeSet() {
        AuthPrincipal principal = new AuthPrincipal();

        assertThat(principal.isBound()).isFalse();
        assertThat(principal.isInitial()).isFalse();
        assertThat(principal.isVerify()).isFalse();
    }

    /** \u9a8c\u8bc1<code>principal.getAppId()</code> \u7b49\u4e8e <code>"app-1"</code>\uff0c<code>principal.getAppChannel()</code> \u7b49\u4e8e <code>"web"</code>\uff0c<code>principal.getUserAgent()</code> \u7b49\u4e8e <code>"ua"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>role.getRoleId()</code> \u7b49\u4e8e <code>"r1"</code>\uff0c<code>role.getRoleCode()</code> \u7b49\u4e8e <code>"rc"</code>\uff0c<code>role.isVerify()</code>\u4e3a\u771f\u3002 */
    @Test
    void rolePair_chainedSetters_shouldRoundTrip() {
        AuthPrincipal.RolePair role = new AuthPrincipal.RolePair()
                .setRoleId("r1").setRoleCode("rc").setRoleName("rn").setVerify(true);

        assertThat(role.getRoleId()).isEqualTo("r1");
        assertThat(role.getRoleCode()).isEqualTo("rc");
        assertThat(role.getRoleName()).isEqualTo("rn");
        assertThat(role.isVerify()).isTrue();
    }

    /** \u9a8c\u8bc1<code>principal.getOrgId()</code> \u7b49\u4e8e <code>"org-1"</code>\uff0c<code>principal.getUserType()</code> \u7b49\u4e8e <code>"internal"</code>\u3002 */
    @Test
    void orgIdAndUserType_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setOrgId("org-1").setUserType("internal");

        assertThat(principal.getOrgId()).isEqualTo("org-1");
        assertThat(principal.getUserType()).isEqualTo("internal");
    }
}
