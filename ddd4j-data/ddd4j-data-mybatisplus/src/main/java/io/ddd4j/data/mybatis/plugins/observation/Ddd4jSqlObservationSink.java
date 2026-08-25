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
package io.ddd4j.data.mybatis.plugins.observation;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;

import java.util.Objects;

/**
 * 将 MyBatis-Plus SQL 观测结果写入 ddd4j 请求上下文。
 */
public class Ddd4jSqlObservationSink implements SqlObservationSink {

    @Override
    public void accept(SqlObservation observation) {
        if (Objects.isNull(observation)) {
            return;
        }
        String sql = observation.sql();
        if (Objects.nonNull(sql)) {
            ThreadContext.set(ContextConstants.PREPARING_SQL, sql.replaceAll("\\s+", " ").trim());
        }
        ThreadContext.set(ContextConstants.LAST_SQL_SPENDS, observation.elapsedMillis());
    }
}
