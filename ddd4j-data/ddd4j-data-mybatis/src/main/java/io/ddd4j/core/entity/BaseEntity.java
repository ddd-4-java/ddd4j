/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MyBatis Plus ActiveRecord 轨道的实体基类。
 *
 * @param <T> 实体类型
 * @author wandl
 * @since 1.0.x
 * @deprecated 自 3.4.x 起，ddd4j-boot 重构为纯 DDD 脚手架。本类继承 MyBatis Plus 的
 * {@link Model}（ActiveRecord 模式），导致领域层耦合 ORM 框架，违反 DDD 领域纯净性原则。
 * <p>
 * <b>替代方案</b>（按场景选择）：
 * <ul>
 *   <li><b>纯净 DDD 轨道</b>：使用 {@link io.ddd4j.core.contract.Model}
 *       （不继承任何框架类，适合 COLA/Clean/Hexagonal 架构）</li>
 *   <li><b>事件溯源轨道</b>：使用 {@code io.ddd4j.ddd.aggregate.DddAggregateRoot}
 *       （基于 fuinorg ddd-4-java，支持 ES/CQRS）</li>
 * </ul>
 * <p>
 * 本类将在 5.0.x 版本移除。迁移期间保留向后兼容。
 */
@Deprecated(since = "3.4.x", forRemoval = true)
@Getter
@Setter
public class BaseEntity<T extends Model<?>> extends Model<T> implements Serializable {

    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;

    @TableField("create_by")
    private Long createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField("update_by")
    private Long updateBy;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableField(exist = false)
    private LocalDateTime beginTime;

    @JsonIgnore
    @TableField(exist = false)
    private LocalDateTime endTime;

    @JsonIgnore
    @TableField(exist = false)
    private String keywords;

    @JsonIgnore
    @TableField(exist = false)
    private String remark;

    @JsonIgnore
    @TableField(exist = false)
    private Map<String, Object> params;

    public Map<String, Object> getParams() {
        if (Objects.isNull(params)) {
            params = new HashMap<>();
        }
        return params;
    }

}
