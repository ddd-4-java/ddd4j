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

@SpringBootTest(classes = SpringJpaProjectionPositionTestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:runtimeprojection;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class SpringJpaProjectionPositionDatabaseTest {

    @Autowired
    private SpringJpaProjectionPositionRepository positions;

    @Autowired
    private SpringJpaProjectionPositionDataRepository data;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        data.deleteAll();
    }

    @Test
    void resetExistingPositionUpdatesDatabase() {
        data.saveAndFlush(new SpringJpaProjectionPosition("orders", 5L));

        positions.resetToZero("orders");

        assertThat(databasePosition("orders")).isZero();
    }

    @Test
    void resetMissingPositionCreatesDatabaseRow() {
        positions.resetToZero("missing");

        assertThat(databasePosition("missing")).isZero();
    }

    private long databasePosition(String streamId) {
        String sql = "SELECT " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER
                + " FROM " + ProjectionConstants.TABLE_NAME
                + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";
        return jdbc.queryForObject(sql, Long.class, streamId);
    }
}
