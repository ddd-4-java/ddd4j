/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.auth.security.jwt;

import com.github.hiwepy.jwt.JwtClaims;
import com.github.hiwepy.jwt.JwtPayload;
import com.github.hiwepy.jwt.exception.ExpiredJwtException;
import com.github.hiwepy.jwt.exception.IncorrectJwtException;
import com.github.hiwepy.jwt.exception.InvalidJwtToken;
import com.github.hiwepy.jwt.exception.JwtException;
import com.github.hiwepy.jwt.token.SignedWithSecretKeyJWTRepository;
import io.ddd4j.auth.security.SecurityConstants;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.boot.biz.userdetails.JwtPayloadRepository;
import org.springframework.security.boot.biz.userdetails.SecurityPrincipal;
import org.springframework.security.boot.biz.userdetails.UserProfilePayload;
import org.springframework.security.boot.jwt.exception.AuthenticationJwtExpiredException;
import org.springframework.security.boot.jwt.exception.AuthenticationJwtIncorrectException;
import org.springframework.security.boot.jwt.exception.AuthenticationJwtInvalidException;
import org.springframework.security.boot.jwt.exception.AuthenticationJwtIssuedException;
import org.springframework.security.core.AuthenticationException;

import java.security.Key;
import java.util.*;

@Slf4j
public abstract class AbstractJwtPayloadRepository implements JwtPayloadRepository {

    private SignedWithSecretKeyJWTRepository secretKeyJWTRepository;
    private RedisOperationTemplate redisOperationTemplate;
    private JwtIssueProperteis jwtIssueProperteis;

    public AbstractJwtPayloadRepository(SignedWithSecretKeyJWTRepository secretKeyJWTRepository,
                                        RedisOperationTemplate redisOperationTemplate,
                                        JwtIssueProperteis jwtIssueProperteis) {
        super();
        this.secretKeyJWTRepository = secretKeyJWTRepository;
        this.redisOperationTemplate = redisOperationTemplate;
        this.jwtIssueProperteis = jwtIssueProperteis;
    }

    /**
     * 获取用于对JWT进行数字签名的签名密钥。
     * @return
     */
    abstract Key getSigningKey();

    /**
     * 获取用于验证任何发现的JWS数字签名的算法特定签名验证密钥。
     * @return
     */
    abstract Key getVerificationKey();

    @Override
    public String issueJwt(AbstractAuthenticationToken token) {
        SecurityPrincipal principal = (SecurityPrincipal) token.getPrincipal();
        return this.issueJwt(principal);
    }

    @Override
    public String issueJwt(SecurityPrincipal principal) {

        Map<String, Object> claims = new HashMap<>();

        claims.put(JwtClaims.UID, principal.getUid());
        claims.put(JwtClaims.UUID, principal.getUuid());
        claims.put(JwtClaims.RID, principal.getRid());
        claims.put(JwtClaims.RKEY, principal.getRkey());
        claims.put(JwtClaims.BOUND, principal.isBound());
        claims.put(JwtClaims.INITIAL, principal.isInitial());
        //claims.put(JwtClaims.VERIFY, principal.isVerify());
        // 设置perms（会导致token边长，备用方案）
		/*Set<String> perms = principal.getPerms();
		if (CollectionUtils.isNotEmpty(perms)) {
			StringBuilder builder = new StringBuilder();
			for (String perm : perms) {
				builder.append(perm).append(",");
			}
			String permsString = builder.toString();
			permsString = builder.delete(permsString.length()-1, permsString.length()).toString();
			claims.put(JwtClaims.PERMS, permsString);
		}*/

        // 4、查询是否缓存（在线用户缓存数据）
        if (MapUtils.isNotEmpty(principal.getProfile())) {

            Map<String, Object> profile = new HashMap<>();

            profile.put(SecurityConstants.PAYLOAD_ACCOUNT_ID, MapUtils.getString(principal.getProfile(), SecurityConstants.PAYLOAD_ACCOUNT_ID));
            profile.put(SecurityConstants.PAYLOAD_USER_ID, MapUtils.getString(principal.getProfile(), SecurityConstants.PAYLOAD_USER_ID));
            profile.put(SecurityConstants.PAYLOAD_ORG_ID, MapUtils.getString(principal.getProfile(), SecurityConstants.PAYLOAD_ORG_ID));
            profile.put(SecurityConstants.PAYLOAD_DEPT_ID, MapUtils.getString(principal.getProfile(), SecurityConstants.PAYLOAD_DEPT_ID));
            profile.put(SecurityConstants.PAYLOAD_IDENTITY_ID, MapUtils.getString(principal.getProfile(), SecurityConstants.PAYLOAD_IDENTITY_ID));
            profile.put(SecurityConstants.PAYLOAD_TENANT_ID, MapUtils.getString(principal.getProfile(), SecurityConstants.PAYLOAD_TENANT_ID));
            //profile.put(SecurityConstants.PHONE, MapUtils.getString(principal.getProfile(), SecurityConstants.PHONE));
            //profile.put(SecurityConstants.NICKNAME, MapUtils.getString(principal.getProfile(), SecurityConstants.NICKNAME));
            //profile.put(SecurityConstants.IDCARD, MapUtils.getString(principal.getProfile(), SecurityConstants.IDCARD));
            claims.put(JwtClaims.PROFILE, profile);

        }

        return this.issueJwt(principal.getUid(), claims);
    }

    @Override
    public String issueJwt(String userId, Map<String, Object> claims) {
        try {
            // 1、签发Token
            String jwtId = UUID.randomUUID().toString();
            String jwtString = getSecretKeyJWTRepository().issueJwt(this.getSigningKey(), jwtId, userId,
                    jwtIssueProperteis.getIssuer(), userId, claims, jwtIssueProperteis.getAlgorithm(),
                    jwtIssueProperteis.getExpire().toMillis());
            // 2、设置Redis缓存
            if(Objects.nonNull(jwtIssueProperteis.getExpire())){
                getRedisOperationTemplate().set(jwtId, jwtString, jwtIssueProperteis.getExpire());
            } else {
                getRedisOperationTemplate().set(jwtId, jwtString);
            }
            return jwtString;
        } catch (JwtException e) {
            throw new AuthenticationJwtIssuedException("JWT issue error");
        }
    }

    @Override
    public boolean verify(AbstractAuthenticationToken token, boolean checkExpiry) throws AuthenticationException {
        // 1、获取 token
        String jwtString = String.valueOf(token.getCredentials());
        // 2、验证 token
        return this.verify(jwtString, checkExpiry);
    }

    @Override
    public boolean verify(String token, boolean checkExpiry) throws AuthenticationException {
        try {

            // 1、获取 JWT 解析器
            JwtParser jwtParser = getSecretKeyJWTRepository().getJwtParser(this.getVerificationKey(), checkExpiry);

            // 2、解密JWT，如果无效则会抛出异常
            Jws<Claims> jws = jwtParser.parseClaimsJws(token);

            // 3、获取JWT的Claims
            Claims claims = jws.getBody();
            Date issuedAt = claims.getIssuedAt();
            Date notBefore = claims.getNotBefore();
            Date expiration = claims.getExpiration();
            Date now = getSecretKeyJWTRepository().getClock().now();
            if (log.isDebugEnabled()) {
                log.debug("JWT IssuedAt:" + issuedAt);
                log.debug("JWT NotBefore:" + notBefore);
                log.debug("JWT Expiration:" + expiration);
                log.debug("JWT Now:" + now);
            }

            // 4、检查 JWT 是否在有效期内
            if(Objects.nonNull(notBefore) && now.getTime() <= notBefore.getTime()) {
                log.warn("JWT was not obtained before this timestamp : [{}].", notBefore);
                return Boolean.FALSE;
            }
            if(Objects.nonNull(expiration) && expiration.getTime() < now.getTime()) {
                log.warn("JWT has expired : [{}].", expiration);
                return Boolean.FALSE;
            }
            // 5、检查JWT是否在缓存中（用于会话控制）
            if (!getRedisOperationTemplate().hasKey(claims.getId())) {
                log.warn("The JWT session has been deleted from Redis.");
                return Boolean.FALSE;
            }
            // 6、验证通过
            return Boolean.TRUE;
        } catch (MalformedJwtException e) {
            log.error("JWT was not correctly constructed : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (MissingClaimException e) {
            log.error("JWT is missing a required claim : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("JWT has expired : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (InvalidClaimException e) {
            log.error("JWT has an invalid claim : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (PrematureJwtException e) {
            log.error("JWT is used before it is allowed to be : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (RequiredTypeException e) {
            log.error("JWT is expected to have a different type : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (JwtException e) {
            log.error("JWT has an invalid signature : [{}].", e.getMessage());
            return Boolean.FALSE;
        } catch (IllegalArgumentException e) {
            log.error("JWT is not a valid JWS : [{}].", e.getMessage());
            return Boolean.FALSE;
        }
    }

    @Override
    public JwtPayload getPayload(AbstractAuthenticationToken token, boolean checkExpiry) {
        String jwtString = String.valueOf(token.getCredentials());
        return this.getPayload(jwtString, checkExpiry);
    }

    @Override
    public JwtPayload getPayload(String token, boolean checkExpiry) {
        try {

            // 1、解析并检查jwt
            JwtPayload payload = getSecretKeyJWTRepository().getPlayload(this.getVerificationKey(), token, false);
            // 2、检查jwt是否过期
            if (!getRedisOperationTemplate().hasKey(payload.getTokenId())) {
                throw new AuthenticationJwtExpiredException("JWT has expired");
            }
            return payload;
        } catch (ExpiredJwtException e) {
            throw new AuthenticationJwtExpiredException("JWT has expired");
        } catch (InvalidJwtToken e) {
            throw new AuthenticationJwtInvalidException("JWT has invalid");
        } catch (IncorrectJwtException e) {
            throw new AuthenticationJwtIncorrectException("JWT has incorrect");
        }
    }


    @Override
    public UserProfilePayload getProfilePayload(AbstractAuthenticationToken token, boolean checkExpiry) {

        // 1、当前登录用户信息
        SecurityPrincipal principal = (SecurityPrincipal) token.getPrincipal();
        // 2、获取 token
        String tokenString = this.issueJwt(token);
        // 3、构造登录结果对象
        UserProfilePayload payload = principal.toPayload();
        payload.setToken(tokenString);

        return payload;

    }

    public RedisOperationTemplate getRedisOperationTemplate() {
        return redisOperationTemplate;
    }

    public SignedWithSecretKeyJWTRepository getSecretKeyJWTRepository() {
        return secretKeyJWTRepository;
    }

    public JwtIssueProperteis getJwtIssueProperteis() {
        return jwtIssueProperteis;
    }
}
