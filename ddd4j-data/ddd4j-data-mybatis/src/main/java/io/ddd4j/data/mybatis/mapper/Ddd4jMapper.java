/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.mybatis.mapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 原生 MyBatis 聚合仓储所需的最小 CRUD Mapper 契约。
 *
 * <p>该接口不绑定任何 SQL Provider。业务 Mapper 应通过 XML 或 MyBatis 注解为这些方法提供映射，
 * 从而保持原生 MyBatis 的显式 SQL 模型。</p>
 *
 * @param <P> 持久化对象类型
 */
public interface Ddd4jMapper<P> {

    /**
     * 插入一条持久化对象。
     *
     * @param entity 持久化对象
     * @return 影响行数
     */
    int insert(P entity);

    /**
     * 按主键更新一条持久化对象。
     *
     * @param entity 持久化对象（含主键）
     * @return 影响行数
     */
    int updateById(P entity);

    /**
     * 按主键查询单条持久化对象。
     *
     * @param id 主键值
     * @return 持久化对象，不存在时返回 {@code null}
     */
    P selectById(Serializable id);

    /**
     * 按主键集合批量查询持久化对象。
     *
     * @param ids 主键集合
     * @return 匹配的持久化对象列表
     */
    List<P> selectBatchIds(Collection<? extends Serializable> ids);

    /**
     * 查询全部持久化对象。
     *
     * @return 全部持久化对象列表
     */
    List<P> selectList();
}
