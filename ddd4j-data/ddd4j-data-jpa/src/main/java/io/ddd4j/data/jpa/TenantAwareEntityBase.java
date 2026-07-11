package io.ddd4j.data.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 标准 Jakarta Persistence 审计实体基类。
 *
 * <p>本类型不依赖 Hibernate 或 Quarkus Panache。查询、分页与租户过滤由
 * Repository 适配器负责，持久化对象只承载映射和审计状态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@MappedSuperclass
public abstract class TenantAwareEntityBase {

    @Column(name = "created_time", updatable = false)
    protected LocalDateTime createdTime;

    @Column(name = "updated_time")
    protected LocalDateTime updatedTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(createdTime)) {
            createdTime = now;
        }
        updatedTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
