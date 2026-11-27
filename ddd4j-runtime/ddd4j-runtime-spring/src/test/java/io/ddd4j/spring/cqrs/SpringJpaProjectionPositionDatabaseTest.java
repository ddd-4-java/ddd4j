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

/** \u901a\u8fc7 <code>data.saveAndFlush()</code>\u3001<code>positions.resetToZero()</code>\u3001<code>databasePosition()</code> \u9a8c\u8bc1 <code>SpringJpaProjectionPositionDatabase</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@SpringBootTest(classes = SpringJpaProjectionPositionTestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:runtimeprojection;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class SpringJpaProjectionPositionDatabaseTest {

    /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
    @Autowired
    private SpringJpaProjectionPositionRepository positions;

    /** \u7528\u4e8e\u6784\u9020\u8bf7\u6c42\u6216\u68c0\u67e5\u8bfb\u5199\u7ed3\u679c\u7684\u6d4b\u8bd5\u4e1a\u52a1\u6570\u636e\u3002 */
    @Autowired
    private SpringJpaProjectionPositionDataRepository data;

    /** \u8d1f\u8d23\u8bbf\u95ee\u5f53\u524d\u6d4b\u8bd5\u6570\u636e\u5e93\u7684 <code>JdbcTemplate</code> \u5165\u53e3\u3002 */
    @Autowired
    private JdbcTemplate jdbc;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>data.deleteAll()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void clear() {
        data.deleteAll();
    }

    /** \u9a8c\u8bc1<code>databasePosition("orders")</code>\u4e3a\u96f6\u3002 */
    @Test
    void resetExistingPositionUpdatesDatabase() {
        data.saveAndFlush(new SpringJpaProjectionPosition("orders", 5L));

        positions.resetToZero("orders");

        assertThat(databasePosition("orders")).isZero();
    }

    /** \u9a8c\u8bc1<code>databasePosition("missing")</code>\u4e3a\u96f6\u3002 */
    @Test
    void resetMissingPositionCreatesDatabaseRow() {
        positions.resetToZero("missing");

        assertThat(databasePosition("missing")).isZero();
    }

    /**
     * \u901a\u8fc7 <code>jdbc.queryForObject()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param streamId \u672c\u6b21\u6295\u5f71\u64cd\u4f5c\u4f7f\u7528\u7684\u4e8b\u4ef6\u6d41\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>jdbc.queryForObject(sql, Long.class, streamId)</code>
     */
    private long databasePosition(String streamId) {
        String sql = "SELECT " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER
                + " FROM " + ProjectionConstants.TABLE_NAME
                + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";
        return jdbc.queryForObject(sql, Long.class, streamId);
    }
}
