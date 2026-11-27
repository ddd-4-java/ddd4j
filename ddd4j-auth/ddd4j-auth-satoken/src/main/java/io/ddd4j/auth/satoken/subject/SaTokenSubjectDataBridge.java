package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.stp.StpInterface;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.util.SubjectKit;

import java.util.List;
import java.util.Objects;

/**
 * Bridges Sa-Token annotation authorization to ddd4j's SubjectDataProvider.
 */
public class SaTokenSubjectDataBridge implements StpInterface {

    /**
     * \u8bfb\u53d6\u6307\u5b9a\u8ba4\u8bc1\u57df\u4e2d\u7684\u8d26\u53f7\u4e3b\u4f53\u5e76\u67e5\u8be2\u4e1a\u52a1\u6743\u9650\u3002
     * @param loginId \u5f53\u524d\u8ba4\u8bc1\u57df\u5185\u7684\u8d26\u53f7\u6807\u8bc6
     * @param loginType \u6240\u5c5e\u7684 Sa-Token \u8ba4\u8bc1\u57df
     * @return \u8d26\u53f7\u6743\u9650\u5217\u8868\uff0c\u627e\u4e0d\u5230\u4e3b\u4f53\u65f6\u8fd4\u56de\u7a7a\u5217\u8868
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        AuthPrincipal principal = SubjectKit.getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return List.of();
        }
        return SubjectKit.getDataProvider().getPermissionList(principal);
    }

    /**
     * \u8bfb\u53d6\u6307\u5b9a\u8ba4\u8bc1\u57df\u4e2d\u7684\u8d26\u53f7\u4e3b\u4f53\u5e76\u67e5\u8be2\u4e1a\u52a1\u89d2\u8272\u3002
     * @param loginId \u5f53\u524d\u8ba4\u8bc1\u57df\u5185\u7684\u8d26\u53f7\u6807\u8bc6
     * @param loginType \u6240\u5c5e\u7684 Sa-Token \u8ba4\u8bc1\u57df
     * @return \u8d26\u53f7\u89d2\u8272\u5217\u8868\uff0c\u627e\u4e0d\u5230\u4e3b\u4f53\u65f6\u8fd4\u56de\u7a7a\u5217\u8868
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AuthPrincipal principal = SubjectKit.getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return List.of();
        }
        return SubjectKit.getDataProvider().getRoleList(principal);
    }
}
