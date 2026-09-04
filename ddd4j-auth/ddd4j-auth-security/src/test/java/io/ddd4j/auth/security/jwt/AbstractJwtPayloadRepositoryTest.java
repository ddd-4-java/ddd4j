package io.ddd4j.auth.security.jwt;

import com.github.hiwepy.jwt.JwtClaims;
import com.github.hiwepy.jwt.exception.JwtException;
import com.github.hiwepy.jwt.token.SignedWithSecretKeyJWTRepository;
import io.ddd4j.auth.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.boot.biz.userdetails.SecurityPrincipal;
import org.springframework.security.boot.jwt.exception.AuthenticationJwtIssuedException;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AbstractJwtPayloadRepository} tests.
 */
class AbstractJwtPayloadRepositoryTest {

    private SignedWithSecretKeyJWTRepository secretKeyJWTRepository;
    private RedisOperationTemplate redisOperationTemplate;
    private JwtIssueProperteis jwtIssueProperteis;
    private AbstractJwtPayloadRepository repository;

    @BeforeEach
    void setUp() {
        secretKeyJWTRepository = mock(SignedWithSecretKeyJWTRepository.class);
        redisOperationTemplate = mock(RedisOperationTemplate.class);
        jwtIssueProperteis = mock(JwtIssueProperteis.class);
        when(jwtIssueProperteis.getIssuer()).thenReturn("ddd4j-test");
        when(jwtIssueProperteis.getAlgorithm()).thenReturn("HS256");
        when(jwtIssueProperteis.getExpire()).thenReturn(Duration.ofHours(1));

        Key key = new SecretKeySpec("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        repository = new AbstractJwtPayloadRepository(secretKeyJWTRepository, redisOperationTemplate, jwtIssueProperteis) {
            @Override
            Key getSigningKey() {
                return key;
            }

            @Override
            Key getVerificationKey() {
                return key;
            }
        };
    }

    @Test
    void shouldIssueJwtStringForAuthenticationToken() {
        SecurityPrincipal principal = stubPrincipal(null);
        AbstractAuthenticationToken token = mock(AbstractAuthenticationToken.class);
        when(token.getPrincipal()).thenReturn(principal);
        when(secretKeyJWTRepository.issueJwt(any(Key.class), anyString(), eq("uid-1"), eq("ddd4j-test"), eq("uid-1"),
                anyMap(), eq("HS256"), anyLong())).thenReturn("jwt-token-1");

        String jwt = repository.issueJwt(token);

        assertEquals("jwt-token-1", jwt);
    }

    @Test
    void shouldIncludeClaimsFromSecurityPrincipal() {
        Map<String, Object> profile = new HashMap<>();
        profile.put(SecurityConstants.PAYLOAD_ACCOUNT_ID, "acc-1");
        profile.put(SecurityConstants.PAYLOAD_USER_ID, "user-9");
        profile.put(SecurityConstants.PAYLOAD_TENANT_ID, "tenant-1");
        SecurityPrincipal principal = stubPrincipal(profile);
        when(secretKeyJWTRepository.issueJwt(any(Key.class), anyString(), anyString(), anyString(), anyString(),
                anyMap(), anyString(), anyLong())).thenReturn("jwt-token-2");

        String jwt = repository.issueJwt(principal);

        assertEquals("jwt-token-2", jwt);
        ArgumentCaptor<Map> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(secretKeyJWTRepository).issueJwt(any(Key.class), anyString(), eq("uid-1"), eq("ddd4j-test"), eq("uid-1"),
                claimsCaptor.capture(), eq("HS256"), anyLong());
        Map<String, Object> claims = claimsCaptor.getValue();
        assertEquals("uid-1", claims.get(JwtClaims.UID));
        assertEquals("uuid-1", claims.get(JwtClaims.UUID));
        assertEquals("rid-1", claims.get(JwtClaims.RID));
        assertEquals("rkey-1", claims.get(JwtClaims.RKEY));
        assertEquals(Boolean.TRUE, claims.get(JwtClaims.BOUND));
        assertEquals(Boolean.FALSE, claims.get(JwtClaims.INITIAL));
        // profile 非空分支：应包含 PROFILE 声明且携带用户/租户信息
        Object profileClaim = claims.get(JwtClaims.PROFILE);
        assertNotNull(profileClaim, "profile claim should be present when principal profile is not empty");
        assertTrue(profileClaim instanceof Map);
        Map<?, ?> profileMap = (Map<?, ?>) profileClaim;
        assertEquals("user-9", profileMap.get(SecurityConstants.PAYLOAD_USER_ID));
        assertEquals("tenant-1", profileMap.get(SecurityConstants.PAYLOAD_TENANT_ID));
    }

    @Test
    void shouldStoreJwtInRedisWithSameTtl() {
        Duration expire = Duration.ofHours(2);
        when(jwtIssueProperteis.getExpire()).thenReturn(expire);
        when(secretKeyJWTRepository.issueJwt(any(Key.class), anyString(), anyString(), anyString(), anyString(),
                anyMap(), anyString(), anyLong())).thenReturn("jwt-abc");

        String jwt = repository.issueJwt("uid-1", new HashMap<String, Object>());

        assertEquals("jwt-abc", jwt);
        ArgumentCaptor<String> jwtIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisOperationTemplate).set(jwtIdCaptor.capture(), eq("jwt-abc"), eq(expire));
        assertNotNull(jwtIdCaptor.getValue());
        // period 参数应与 expire 毫秒数一致
        verify(secretKeyJWTRepository).issueJwt(any(Key.class), anyString(), anyString(), anyString(), anyString(),
                anyMap(), anyString(), eq(expire.toMillis()));
    }

    @Test
    void shouldWrapJwtExceptionAsAuthenticationJwtIssuedException() {
        when(secretKeyJWTRepository.issueJwt(any(Key.class), anyString(), anyString(), anyString(), anyString(),
                anyMap(), anyString(), anyLong())).thenThrow(new JwtException("sign error"));

        assertThrows(AuthenticationJwtIssuedException.class,
                () -> repository.issueJwt("uid-1", new HashMap<String, Object>()));
    }

    @Test
    void shouldGenerateUniqueJwtIdForEachCall() {
        when(secretKeyJWTRepository.issueJwt(any(Key.class), anyString(), anyString(), anyString(), anyString(),
                anyMap(), anyString(), anyLong())).thenReturn("jwt-1", "jwt-2");

        repository.issueJwt("uid-1", new HashMap<String, Object>());
        repository.issueJwt("uid-1", new HashMap<String, Object>());

        ArgumentCaptor<String> jwtIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisOperationTemplate, org.mockito.Mockito.times(2))
                .set(jwtIdCaptor.capture(), anyString(), any(Duration.class));
        List<String> jwtIds = jwtIdCaptor.getAllValues();
        assertEquals(2, jwtIds.size());
        assertNotEquals(jwtIds.get(0), jwtIds.get(1));
    }

    // ========================= Fixtures =========================

    private SecurityPrincipal stubPrincipal(Map<String, Object> profile) {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.getUid()).thenReturn("uid-1");
        when(principal.getUuid()).thenReturn("uuid-1");
        when(principal.getRid()).thenReturn("rid-1");
        when(principal.getRkey()).thenReturn("rkey-1");
        when(principal.isBound()).thenReturn(true);
        when(principal.isInitial()).thenReturn(false);
        when(principal.getProfile()).thenReturn(profile);
        return principal;
    }
}
