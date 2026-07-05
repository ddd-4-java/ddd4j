package io.ddd4j.sample.quarkus.satoken.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * RBAC 用户实体。
 *
 * <p>演示用最小字段集：用户标识、登录名、显示名、密码（明文演示用）、角色集合、禁用标志。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** 用户 ID（业务主键）。 */
    private String id;
    /** 登录名（唯一）。 */
    private String username;
    /** 显示名。 */
    private String displayName;
    /** 密码（演示用明文）。 */
    private String password;
    /** 已绑定的角色编码集合。 */
    private Set<String> roleCodes = new HashSet<>();
    /** 是否禁用。 */
    private boolean disabled;

}