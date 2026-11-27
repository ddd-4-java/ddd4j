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

    /** \u5b9e\u4f53\u4e3b\u952e\u3002 */
    @Id
    @Column(name = "id")
    protected Long id;

    /** \u79df\u6237\u6807\u8bc6\u3002 */
    @Id
    @Column(name = "tenant_id")
    protected String tenantId;

    /** \u9996\u6b21\u6301\u4e45\u5316\u524d\u5206\u914d\u96ea\u82b1\u4e3b\u952e\u3002 */
    @PrePersist
    protected void assignIdentity() {
        if (Objects.isNull(id)) {
            id = SnowflakeIdGenerator.nextId();
        }
    }

    /**
     * \u8fd4\u56de\u5b9e\u4f53\u4e3b\u952e\u3002
     * @return \u5b9e\u4f53\u4e3b\u952e
     */
    public Long getId() {
        return id;
    }

    /**
     * \u8fd4\u56de\u79df\u6237\u6807\u8bc6\u3002
     * @return \u79df\u6237\u6807\u8bc6
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 生成用于调试和日志的对象描述。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return String.format("%s<%s:%s>", this.getClass().getSimpleName(), tenantId, id);
    }
}
