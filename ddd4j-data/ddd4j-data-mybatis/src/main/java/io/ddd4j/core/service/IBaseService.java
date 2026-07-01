/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.service;

import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import io.ddd4j.core.entity.PaginationEntity;

import java.io.Serializable;

/**
 * 通用 Service 接口（MyBatis Plus 轨道）。
 *
 * @param <T> 持有的实体对象
 * @author <a href="https://github.com/wandl">wandl</a>
 * @deprecated 自 3.4.x 起，ddd4j-boot 重构为纯 DDD 脚手架。本接口继承 MyBatis Plus 的
 * {@link IService}，导致领域层耦合 ORM 框架。
 * <p>
 * <b>替代方案</b>：使用 {@link io.ddd4j.core.contract.Repository}
 * （框架无关的仓储接口，领域层定义、基础设施层实现）。
 * <p>
 * 本接口将在 5.0.x 版本移除。
 */
@Deprecated(since = "3.4.x", forRemoval = true)
@SuppressWarnings("removal")
public interface IBaseService<T extends Model<?>> extends IService<T> {

    boolean setStatus(Serializable id, Serializable status);

    Page<T> getPagedList(PaginationEntity<T> entity);

    Page<T> getPagedList(Page<T> page, PaginationEntity<T> entity);

    Long getCountByUid(Serializable uid);

    Long getCountByCode(String code, Object origin);

    Long getCountByName(String name, Object origin);

    Long getCountByParent(Object parent);

    String getValue(String key);

}
