package io.ddd4j.sample.quarkus.shiro.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * RBAC 角色实体。
 *
 * <p>演示用最小字段集：角色编码、显示名、描述、权限编码集合。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /** 角色编码（业务主键，如 {@code admin} / {@code user}）。 */
    private String code;
    /** 显示名。 */
    private String displayName;
    /** 描述。 */
    private String description;
    /** 角色拥有的权限编码集合。 */
    private Set<String> permissionCodes = new HashSet<>();

}