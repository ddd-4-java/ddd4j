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
package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.guice.cqrs.GuiceJdbcProjectionPositionRepository;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * ddd4j 投影位置 JDBC 持久化的 Guice 桥接模块。
 *
 * <p>覆盖 {@link Ddd4jGuiceModule} 默认的内存版 {@link ProjectionPositionRepository}，
 * 将投影位置持久化到关系数据库，重启后不丢失进度。
 *
 * <p>使用方式（推荐 {@code Modules.override} 覆盖默认内存绑定）：
 * <pre>{@code
 * DataSource dataSource = ... // 由业务方提供（如 HikariCP）
 * Injector injector = Guice.createInjector(
 *     Modules.override(new Ddd4jGuiceModule())
 *            .with(new Ddd4jJdbcProjectionGuiceModule(dataSource))
 * );
 * }</pre>
 *
 * <p>也可单独安装（不安装 {@link Ddd4jGuiceModule} 时无绑定冲突）：
 * <pre>{@code
 * Injector injector = Guice.createInjector(
 *     new Ddd4jJdbcProjectionGuiceModule(dataSource)
 * );
 * }</pre>
 *
 * <p>表名统一为 {@code DDD4J_PROJECTION_POSITION}，与 Spring/Quarkus 运行时保持一致。
 * 建表语句（CREATE TABLE IF NOT EXISTS）在模块初始化时自动执行。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see Ddd4jGuiceModule
 * @see GuiceJdbcProjectionPositionRepository
 * @since 3.0.x
 */
@Slf4j
public class Ddd4jJdbcProjectionGuiceModule extends AbstractModule {

    private final DataSource dataSource;

    /**
     * 创建 JDBC 投影位置 Guice 模块。
     *
     * @param dataSource JDBC 数据源（不得为 null）
     */
    public Ddd4jJdbcProjectionGuiceModule(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    protected void configure() {
        bind(ProjectionPositionRepository.class)
                .to(GuiceJdbcProjectionPositionRepository.class)
                .in(Singleton.class);
        // 注意：不在此模块绑定 DataSource，避免与 Ddd4jMybatisGuiceModule 等模块冲突。
        // GuiceJdbcProjectionPositionRepository 通过构造注入接收 DataSource，
        // 业务方需确保 Injector 中已有 DataSource 绑定。
        log.info("Ddd4jJdbcProjectionGuiceModule configured: ProjectionPositionRepository -> JDBC");
    }
}
