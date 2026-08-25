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
package io.ddd4j.data.mybatis.plugins;

/**
 * P1-4: 数据权限条件提供器 SPI
 * <p>
 * 业务项目实现此接口，按当前登录用户 Subject 拼接数据范围条件（如
 * {@code "dept_id IN (1,2,3)"} 或 {@code "create_by = 'admin'"}）。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface DataScopeProvider {

    /**
     * 根据当前登录用户与 Mapper 方法 ID，构造追加到 WHERE 子句的 SQL 片段（不含 WHERE 关键字）。
     *
     * @param mappedStatementId MyBatis MappedStatement ID
     * @return 数据范围条件 SQL 片段；返回 null 或空字符串表示不追加
     */
    String dataScopeCondition(String mappedStatementId);

}
