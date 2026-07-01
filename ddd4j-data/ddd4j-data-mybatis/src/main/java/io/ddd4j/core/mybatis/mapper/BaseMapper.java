/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.mybatis.mapper;

import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.ddd4j.core.entity.PaginationEntity;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.util.List;

/**
 * 通用 Dao 接口（MyBatis Plus 轨道）。
 *
 * @param <T> 持有的实体对象
 * @author <a href="https://github.com/wandl">wandl</a>
 */
@Deprecated(since = "3.4.x", forRemoval = true)
@SuppressWarnings("removal")
public interface BaseMapper<T extends Model<?>> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {

    int setStatus(@Param("id") Serializable id, @Param("status") Serializable status);

    List<T> getPagedList(Page<T> page, @Param("model") PaginationEntity<T> entity);

    Long getCountByUid(@Param("uid") Serializable uid);

    Long getCountByCode(@Param("code") String code, @Param("origin") Object origin);

    Long getCountByName(@Param("name") String name, @Param("origin") Object origin);

    Long getCountByParent(@Param("parent") Object parent);

    String getValue(@Param("key") String key);

}
