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

    /** \u9a8c\u8bc1<code>request.getLoginId()</code> \u7b49\u4e8e <code>"u1"</code>\u3002 */
    @Test
    void of_shouldCreateWithLoginId() {
        AuthRequest request = AuthRequest.of("u1");

        assertThat(request.getLoginId()).isEqualTo("u1");
    }

    /** \u9a8c\u8bc1<code>request.getLoginId()</code> \u7b49\u4e8e <code>42L</code>\u3002 */
    @Test
    void constructor_shouldSetLoginId() {
        AuthRequest request = new AuthRequest(42L);

        assertThat(request.getLoginId()).isEqualTo(42L);
    }

    /** \u9a8c\u8bc1<code>request.getLoginIdAsString()</code> \u7b49\u4e8e <code>"123"</code>\u3002 */
    @Test
    void getLoginIdAsString_shouldStringifyLoginId() {
        AuthRequest request = AuthRequest.of(123L);

        assertThat(request.getLoginIdAsString()).isEqualTo("123");
    }

    /** \u9a8c\u8bc1<code>request.getLoginIdAsString()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getLoginIdAsString_shouldReturnNullWhenAbsent() {
        AuthRequest request = new AuthRequest();

        assertThat(request.getLoginIdAsString()).isNull();
    }

    /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>request</code>\uff0c<code>request.getTimeout()</code> \u7b49\u4e8e <code>600L</code>\u3002 */
    @Test
    void setTimeout_shouldBeChainableAndForwardToSessionConfig() {
        AuthRequest request = AuthRequest.of("u1");

        AuthRequest result = request.setTimeout(600L);

        assertThat(result).isSameAs(request);
        assertThat(request.getTimeout()).isEqualTo(600L);
    }

    /** \u9a8c\u8bc1<code>request.getDeviceType()</code> \u7b49\u4e8e <code>"mobile"</code>\u3002 */
    @Test
    void setDeviceType_shouldForwardToSessionConfig() {
        AuthRequest request = AuthRequest.of("u1").setDeviceType("mobile");

        assertThat(request.getDeviceType()).isEqualTo("mobile");
    }

    /** \u9a8c\u8bc1<code>request.getPrincipal()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void setPrincipal_shouldRoundTrip() {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("u1");
        AuthRequest request = AuthRequest.of("u1").setPrincipal(principal);

        assertThat(request.getPrincipal()).isSameAs(principal);
    }

    /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>request</code>\uff0c<code>request.getExtra()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"k"</code>\u3001<code>"v"</code>\u3002 */
    @Test
    void extra_shouldAddEntryAndChain() {
        AuthRequest request = AuthRequest.of("u1");

        AuthRequest result = request.extra("k", "v");

        assertThat(result).isSameAs(request);
        assertThat(request.getExtra()).containsEntry("k", "v");
    }

    /** \u9a8c\u8bc1<code>request.extra(null, "v")</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void extra_shouldRejectNullKey() {
        AuthRequest request = AuthRequest.of("u1");

        assertThatThrownBy(() -> request.extra(null, "v"))
                .isInstanceOf(NullPointerException.class);
    }

    /** \u9a8c\u8bc1<code>a</code> \u7b49\u4e8e <code>b</code>\uff0c<code>a.hashCode()</code> \u7b49\u4e8e <code>b.hashCode()</code>\uff0c<code>a</code> \u4e0d\u7b49\u4e8e <code>c</code>\u3002 */
    @Test
    void equals_shouldBeBasedOnLoginId() {
        AuthRequest a = AuthRequest.of("u1");
        AuthRequest b = AuthRequest.of("u1");
        AuthRequest c = AuthRequest.of("u2");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    /** \u9a8c\u8bc1<code>s</code> \u5305\u542b <code>"u1"</code>\u3002 */
    @Test
    void toString_shouldContainLoginIdAndRealm() {
        AuthRequest request = AuthRequest.of("u1").setRealm("admin");

        String s = request.toString();

        assertThat(s).contains("u1").contains("admin");
    }

    /** \u9a8c\u8bc1<code>request.getRealm()</code> \u7b49\u4e8e <code>"user"</code>\u3002 */
    @Test
    void realm_shouldRoundTrip() {
        AuthRequest request = AuthRequest.of("u1").setRealm("user");

        assertThat(request.getRealm()).isEqualTo("user");
    }
}
