package io.ddd4j.data.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.util.Objects;

/**
 * 标准 JPA 多租户复合主键实体基类。
 *
 * <p>固定使用 {@code (tenantId, id)} 复合主键。ID 在持久化前通过
 * ddd4j 的纯 Java 雪花策略生成，不依赖 Hibernate 专有生成器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@MappedSuperclass
@IdClass(TenantAwareId.class)
public abstract class TenantAwareEntity extends TenantAwareEntityBase {

    @Id
    @Column(name = "id")
    protected Long id;

    @Id
    @Column(name = "tenant_id")
    protected String tenantId;

    @PrePersist
    protected void assignIdentity() {
        if (Objects.isNull(id)) {
            id = SnowflakeIdGenerator.nextId();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return String.format("%s<%s:%s>", this.getClass().getSimpleName(), tenantId, id);
    }
}
