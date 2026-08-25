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

    int insert(P entity);

    int updateById(P entity);

    P selectById(Serializable id);

    List<P> selectBatchIds(Collection<? extends Serializable> ids);

    List<P> selectList();
}
