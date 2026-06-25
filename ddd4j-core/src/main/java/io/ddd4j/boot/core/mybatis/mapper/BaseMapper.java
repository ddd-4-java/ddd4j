/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.core.mybatis.mapper;


import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.ddd4j.boot.core.entity.PaginationEntity;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.util.List;

/**
 * 通用Dao接口
 *
 * @param <T> 持有的实体对象
 * @author <a href="https://github.com/wandl">wandl</a>
 */
@Deprecated(since = "3.4.x", forRemoval = true)
public interface BaseMapper<T extends Model<?>> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {

    /**
     * 更新数据状态
     *
     * @param id     数据ID
     * @param status 数据状态
     * @return 更新结果
     */
    int setStatus(@Param("id") Serializable id, @Param("status") Serializable status);

    /**
     * 分页查询
     *
     * @param page   分页查询参数
     * @param entity 分页查询参数
     * @return 分页查询结果
     */
    List<T> getPagedList(Page<T> page, @Param("model") PaginationEntity<T> entity);

    /**
     * 根据唯一ID编码获取记录数
     *
     * @param uid 唯一ID编码
     * @return 统计记录数
     */
    Long getCountByUid(@Param("uid") Serializable uid);

    /**
     * 根据编码获取记录数
     *
     * @param code   编码
     * @param origin 来源
     * @return 统计记录数
     */
    Long getCountByCode(@Param("code") String code, @Param("origin") Object origin);

    /**
     * 根据名称获取记录数
     *
     * @param name   名称
     * @param origin 来源
     * @return 统计记录数
     */
    Long getCountByName(@Param("name") String name, @Param("origin") Object origin);

    /**
     * 根据父级ID获取记录数
     *
     * @param parent 父级ID
     * @return 统计记录数
     */
    Long getCountByParent(@Param("parent") Object parent);

    /**
     *
     * 通过指定key查询对应的唯一值
     *
     * @param key 键
     * @return 值
     */
    String getValue(@Param("key") String key);

}
