/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.constant.ProjectionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>data.saveAndFlush()</code>、<code>positions.resetToZero()</code>、<code>databasePosition()</code> 验证 <code>SpringJpaProjectionPositionDatabase</code> 的运行行为。
 */
@SpringBootTest(classes = SpringJpaProjectionPositionTestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:runtimeprojection;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class SpringJpaProjectionPositionDatabaseTest {

    /**
     * 测试使用或记录的多条投影位点。
     */
    @Autowired
    private SpringJpaProjectionPositionRepository positions;

    /**
     * 用于构造请求或检查读写结果的测试业务数据。
     */
    @Autowired
    private SpringJpaProjectionPositionDataRepository data;

    /**
     * 负责访问当前测试数据库的 <code>JdbcTemplate</code> 入口。
     */
    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 测试执行前执行 <code>data.deleteAll()</code> 完成资源准备。
     */
    @BeforeEach
    void clear() {
        data.deleteAll();
    }

    /**
     * 验证<code>databasePosition("orders")</code>为零。
     */
    @Test
    void resetExistingPositionUpdatesDatabase() {
        data.saveAndFlush(new SpringJpaProjectionPosition("orders", 5L));

        positions.resetToZero("orders");

        assertThat(databasePosition("orders")).isZero();
    }

    /**
     * 验证<code>databasePosition("missing")</code>为零。
     */
    @Test
    void resetMissingPositionCreatesDatabaseRow() {
        positions.resetToZero("missing");

        assertThat(databasePosition("missing")).isZero();
    }

    /**
     * 通过 <code>jdbc.queryForObject()</code> 取得当前测试需要的结果。
     *
     * @param streamId 本次投影操作使用的事件流标识
     * @return 当前测试使用的结果，取自 <code>jdbc.queryForObject(sql, Long.class, streamId)</code>
     */
    private long databasePosition(String streamId) {
        String sql = "SELECT " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER
                + " FROM " + ProjectionConstants.TABLE_NAME
                + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";
        return jdbc.queryForObject(sql, Long.class, streamId);
    }
}
