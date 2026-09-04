package io.ddd4j.auth.satoken.subject;

import java.util.Collections;
import cn.dev33.satoken.stp.StpInterface;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.util.SubjectKit;

import java.util.List;
import java.util.Objects;

/**
 * Bridges Sa-Token annotation authorization to ddd4j's SubjectDataProvider.
 */
public class SaTokenSubjectDataBridge implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        AuthPrincipal principal = SubjectKit.getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return java.util.Collections.emptyList();
        }
        return SubjectKit.getDataProvider().getPermissionList(principal);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AuthPrincipal principal = SubjectKit.getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return java.util.Collections.emptyList();
        }
        return SubjectKit.getDataProvider().getRoleList(principal);
    }
}
