package io.ddd4j.sample.spring.security.rbac;

import java.util.Objects;

/**
 * RBAC 权限实体。
 *
 * <p>权限编码遵循 {@code 资源:动作} 惯例，例如：
 * <ul>
 *   <li>{@code user:list}  - 用户列表</li>
 *   <li>{@code user:add}   - 新增用户</li>
 *   <li>{@code user:delete}- 删除用户</li>
 *   <li>{@code order:pay}  - 订单支付</li>
 * </ul>
 *
 * <p>Spring Security 中 {@code hasAuthority('user:list')} 直接匹配编码。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Permission {

    private String code;
    private String name;
    private String description;

    public Permission() {
    }

    public Permission(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Permission(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public Permission setCode(String code) {
        this.code = code;
        return this;
    }

    public String getName() {
        return name;
    }

    public Permission setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Permission setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission that)) return false;
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

}