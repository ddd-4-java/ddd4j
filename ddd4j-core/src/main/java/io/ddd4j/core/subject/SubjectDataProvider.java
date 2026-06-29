package io.ddd4j.core.subject;

import java.util.Collections;
import java.util.List;

/**
 * 权限/角色数据源 SPI（对齐 Sa-Token 的 {@code StpInterface}）。
 *
 * <p>ddd4j-auth 框架本身<b>不持有权限数据</b>，由业务实现此接口提供数据源。
 * 各鉴权实现（sa-token/shiro/security）在 {@link Subject#isPermitted} / {@link Subject#hasRole} 时委托此 SPI。
 *
 * <p>使用方式：
 * <pre>
 * SubjectKit.setDataProvider(new SubjectDataProvider() {
 *     public List&lt;String&gt; getPermissionList(AuthPrincipal p) {
 *         return permissionService.findByUserId(p.getUserId());
 *     }
 * });
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public interface SubjectDataProvider {

    /**
     * 默认空实现兜底（对齐 Sa-Token StpInterfaceDefaultImpl）
     */
    SubjectDataProvider DEFAULT = new SubjectDataProvider() {
    };

    /**
     * 返回指定账号拥有的权限码集合。
     *
     * @param principal 认证主体
     * @return 权限码列表，默认空集合
     */
    default List<String> getPermissionList(AuthPrincipal principal) {
        return Collections.emptyList();
    }

    /**
     * 返回指定账号拥有的角色标识集合。
     *
     * @param principal 认证主体
     * @return 角色列表，默认空集合
     */
    default List<String> getRoleList(AuthPrincipal principal) {
        return Collections.emptyList();
    }

    /**
     * 判断指定账号是否被封禁。
     *
     * @param loginId 账号 ID
     * @param service 业务标识
     * @return 是否封禁，默认 false
     */
    default boolean isDisabled(Object loginId, String service) {
        return false;
    }

}
