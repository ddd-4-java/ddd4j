package io.ddd4j.sample.quarkus.satoken.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RBAC 权限实体。
 *
 * <p>演示用最小字段集：权限编码、显示名、描述。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    /** 权限编码（业务主键，如 {@code user:list} / {@code order:pay}）。 */
    private String code;
    /** 显示名。 */
    private String displayName;
    /** 描述。 */
    private String description;

}