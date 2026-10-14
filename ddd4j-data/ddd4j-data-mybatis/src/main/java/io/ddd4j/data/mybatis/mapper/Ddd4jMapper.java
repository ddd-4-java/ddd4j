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
     * 插入持久化对象。
     *
     * @param entity 待插入对象
     * @return 受影响行数
     */
    int insert(P entity);

    /**
     * 按标识更新持久化对象。
     *
     * @param entity 待更新对象
     * @return 受影响行数
     */
    int updateById(P entity);

    /**
     * 按标识查询持久化对象。
     *
     * @param id 对象标识
     * @return 匹配的对象
     */
    P selectById(Serializable id);

    /**
     * 批量按标识查询持久化对象。
     *
     * @param ids 对象标识集合
     * @return 匹配的对象列表
     */
    List<P> selectBatchIds(Collection<? extends Serializable> ids);

    /**
     * 查询全部持久化对象。
     *
     * @return 对象列表
     */
    List<P> selectList();
}
