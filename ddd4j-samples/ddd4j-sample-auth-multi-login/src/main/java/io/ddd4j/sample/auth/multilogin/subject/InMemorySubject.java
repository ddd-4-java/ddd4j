package io.ddd4j.sample.auth.multilogin.subject;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.auth.multilogin.event.LoginFailedEvent;
import io.ddd4j.sample.auth.multilogin.event.LoginSucceededEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySubject implements Subject {

    private static final long PERMANENT_DISABLE = -1L;

    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, AuthPrincipal> principalsByToken = new ConcurrentHashMap<>();
    private final Map<Object, AuthPrincipal> principalsByLoginId = new ConcurrentHashMap<>();
    private final Map<Object, String> tokenByLoginId = new ConcurrentHashMap<>();
    private final Map<Object, Long> disabledUntilEpochSecond = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentToken = new ThreadLocal<>();

    public InMemorySubject(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void bind(String token) {
        if (principalsByToken.containsKey(token)) {
            currentToken.set(token);
        }
    }

    public void clear() {
        currentToken.remove();
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        String token = currentToken.get();
        if (token == null) {
            return null;
        }
        return (T) principalsByToken.get(token);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return (T) principalsByLoginId.get(loginId);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return (T) principalsByToken.get(tokenValue);
    }

    @Override
    public String login(AuthRequest request) {
        try {
            validate(request);
            AuthPrincipal principal = normalizePrincipal(request);
            String token = createToken(request);
            principalsByToken.put(token, principal);
            principalsByLoginId.put(request.getLoginId(), principal);
            tokenByLoginId.put(request.getLoginId(), token);
            currentToken.set(token);
            eventPublisher.publishEvent(new LoginSucceededEvent(request, principal, token, Instant.now()));
            return token;
        } catch (RuntimeException exception) {
            eventPublisher.publishEvent(new LoginFailedEvent(request, exception.getMessage(), Instant.now()));
            throw exception;
        }
    }

    @Override
    public void logout() {
        String token = currentToken.get();
        if (token == null) {
            return;
        }
        AuthPrincipal principal = principalsByToken.remove(token);
        if (principal != null) {
            principalsByLoginId.remove(principal.getLoginId());
            tokenByLoginId.remove(principal.getLoginId());
        }
        currentToken.remove();
    }

    @Override
    public void logout(Object loginId) {
        String token = tokenByLoginId.remove(loginId);
        if (token != null) {
            principalsByToken.remove(token);
        }
        principalsByLoginId.remove(loginId);
        if (Objects.equals(token, currentToken.get())) {
            currentToken.remove();
        }
    }

    @Override
    public void kickout(Object loginId) {
        logout(loginId);
    }

    @Override
    public String refresh() {
        AuthPrincipal principal = getPrincipal();
        if (principal == null) {
            return null;
        }
        String oldToken = currentToken.get();
        String newToken = "refresh:" + UUID.randomUUID();
        principalsByToken.remove(oldToken);
        principalsByToken.put(newToken, principal);
        tokenByLoginId.put(principal.getLoginId(), newToken);
        currentToken.set(newToken);
        return newToken;
    }

    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        return getPrincipalByToken(token);
    }

    @Override
    public boolean isPermitted(String permission) {
        return hasPermission(getPrincipal(), permission);
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return hasPermission(getPrincipalByLoginId(loginId), permission);
    }

    @Override
    public boolean[] isPermitted(String... permissions) {
        return permissionChecks(getPrincipal(), permissions);
    }

    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return permissionChecks(getPrincipalByLoginId(loginId), permissions);
    }

    @Override
    public boolean isPermittedAny(String... permissions) {
        for (boolean permitted : isPermitted(permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        for (boolean permitted : isPermitted(loginId, permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPermittedAll(String... permissions) {
        return allTrue(isPermitted(permissions));
    }

    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return allTrue(isPermitted(loginId, permissions));
    }

    @Override
    public boolean hasRole(String roleIdentifier) {
        return hasRole(getPrincipal(), roleIdentifier);
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(getPrincipalByLoginId(loginId), roleIdentifier);
    }

    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        return roleChecks(getPrincipal(), roleIdentifiers);
    }

    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return roleChecks(getPrincipalByLoginId(loginId), roleIdentifiers);
    }

    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(loginId, roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        return allTrue(hasRoles(roleIdentifiers));
    }

    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return allTrue(hasRoles(loginId, roleIdentifiers));
    }

    @Override
    public boolean isAuthenticated() {
        return getPrincipal() != null;
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return principalsByLoginId.containsKey(loginId);
    }

    @Override
    public boolean isRemembered() {
        return false;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        AuthPrincipal principal = getPrincipal();
        return principal != null && Objects.equals(principal.getDeviceId(), deviceId);
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        AuthPrincipal principal = getPrincipalByLoginId(userId);
        return principal != null && Objects.equals(principal.getDeviceId(), deviceId);
    }

    @Override
    public void disable(Object loginId, long timeout) {
        long disabledUntil = timeout < 0 ? PERMANENT_DISABLE : Instant.now().plusSeconds(timeout).getEpochSecond();
        disabledUntilEpochSecond.put(loginId, disabledUntil);
    }

    @Override
    public boolean isDisabled(Object loginId) {
        Long disabledUntil = disabledUntilEpochSecond.get(loginId);
        if (disabledUntil == null) {
            return false;
        }
        if (disabledUntil == PERMANENT_DISABLE) {
            return true;
        }
        boolean disabled = disabledUntil > Instant.now().getEpochSecond();
        if (!disabled) {
            disabledUntilEpochSecond.remove(loginId);
        }
        return disabled;
    }

    @Override
    public void untieDisable(Object loginId) {
        disabledUntilEpochSecond.remove(loginId);
    }

    private void validate(AuthRequest request) {
        if (request == null || request.getLoginId() == null) {
            throw new IllegalArgumentException("loginId must not be null");
        }
        if (isDisabled(request.getLoginId())) {
            throw new IllegalArgumentException("account is disabled");
        }
        String scene = String.valueOf(request.getExtra().get("loginScene"));
        if ("phone".equals(scene) && !"123456".equals(request.getExtra().get("verificationCode"))) {
            throw new IllegalArgumentException("invalid phone verification code");
        }
        if ("third-party".equals(scene) && isBlank(request.getExtra().get("openId"))) {
            throw new IllegalArgumentException("third-party openId must not be blank");
        }
    }

    private AuthPrincipal normalizePrincipal(AuthRequest request) {
        AuthPrincipal principal = request.getPrincipal();
        if (principal == null) {
            principal = new AuthPrincipal().setLoginId(request.getLoginId()).setUserId(request.getLoginId());
        }
        if (principal.getLoginId() == null) {
            principal.setLoginId(request.getLoginId());
        }
        if (principal.getUserId() == null) {
            principal.setUserId(request.getLoginId());
        }
        if (principal.getDeviceType() == null) {
            principal.setDeviceType(request.getDeviceType());
        }
        principal.getProfile().putAll(request.getExtra());
        principal.getProfile().put("realm", request.getRealm());
        return principal;
    }

    private String createToken(AuthRequest request) {
        String realm = request.getRealm() == null || request.getRealm().isBlank() ? "default" : request.getRealm();
        return realm + ":" + UUID.randomUUID();
    }

    private boolean hasPermission(AuthPrincipal principal, String permission) {
        if (principal == null) {
            return false;
        }
        Set<String> permissions = new HashSet<>(principal.getPerms());
        permissions.addAll(SubjectKit.getDataProvider().getPermissionList(principal));
        return SubjectKit.getStrategy().hasElement.apply(new ArrayList<>(permissions), permission);
    }

    private boolean[] permissionChecks(AuthPrincipal principal, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return new boolean[0];
        }
        boolean[] result = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            result[i] = hasPermission(principal, permissions[i]);
        }
        return result;
    }

    private boolean hasRole(AuthPrincipal principal, String roleIdentifier) {
        if (principal == null) {
            return false;
        }
        return SubjectKit.getStrategy().hasElement.apply(roleIdentifiers(principal), roleIdentifier);
    }

    private boolean[] roleChecks(AuthPrincipal principal, String... roleIdentifiers) {
        if (roleIdentifiers == null || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        boolean[] result = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            result[i] = hasRole(principal, roleIdentifiers[i]);
        }
        return result;
    }

    private List<String> roleIdentifiers(AuthPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        List<String> roles = new ArrayList<>(SubjectKit.getDataProvider().getRoleList(principal));
        if (principal.getRoleCode() != null) {
            roles.add(principal.getRoleCode());
        }
        if (principal.getRoleId() != null) {
            roles.add(String.valueOf(principal.getRoleId()));
        }
        if (principal.getRoles() != null) {
            for (AuthPrincipal.RolePair role : principal.getRoles()) {
                if (role.getRoleCode() != null) {
                    roles.add(role.getRoleCode());
                }
                if (role.getRoleId() != null) {
                    roles.add(role.getRoleId());
                }
            }
        }
        return roles;
    }

    private boolean allTrue(boolean[] values) {
        if (values.length == 0) {
            return false;
        }
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
