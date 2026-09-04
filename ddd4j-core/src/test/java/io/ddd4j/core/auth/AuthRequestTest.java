package io.ddd4j.core.auth;

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
