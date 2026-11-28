/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 <code>SpringJpaProjectionPositionRepository</code> 在<code>findAll</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class SpringJpaProjectionPositionRepositoryTest {

    /**
     * 用于隔离 <code>JpaRepository&lt;SpringJpaProjectionPosition, String&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    private JpaRepository<SpringJpaProjectionPosition, String> jpaRepository;

    /**
     * 验证<code>positions.add(positions.get(0))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void findAllReturnsImmutablePositionListInRepositoryOrder() {
        when(jpaRepository.findAll()).thenReturn(Arrays.asList(
                new SpringJpaProjectionPosition("orders", 2L),
                new SpringJpaProjectionPosition("payments", 5L)));

        List<ProjectionPosition> positions =
                new SpringJpaProjectionPositionRepository(jpaRepository).findAll();

        assertThat(positions).extracting("streamId").containsExactly("orders", "payments");
        assertThat(positions).extracting("nextEventNumber").containsExactly(2L, 5L);
        assertThatThrownBy(() -> positions.add(positions.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证<code>saved.getValue().getStreamId()</code> 等于 <code>"orders"</code>，<code>saved.getValue().getNextEventNumber()</code>为零。
     */
    @Test
    void resetExistingPositionMustPersistZero() {
        when(jpaRepository.findById("orders"))
                .thenReturn(Optional.of(new SpringJpaProjectionPosition("orders", 5L)));

        new SpringJpaProjectionPositionRepository(jpaRepository).resetToZero("orders");

        ArgumentCaptor<SpringJpaProjectionPosition> saved =
                ArgumentCaptor.forClass(SpringJpaProjectionPosition.class);
        verify(jpaRepository).save(saved.capture());
        assertThat(saved.getValue().getStreamId()).isEqualTo("orders");
        assertThat(saved.getValue().getNextEventNumber()).isZero();
    }

    /**
     * 验证<code>saved.getValue().getStreamId()</code> 等于 <code>"missing"</code>，<code>saved.getValue().getNextEventNumber()</code>为零。
     */
    @Test
    void resetMissingPositionMustCreateZero() {
        when(jpaRepository.findById("missing")).thenReturn(Optional.empty());

        new SpringJpaProjectionPositionRepository(jpaRepository).resetToZero("missing");

        ArgumentCaptor<SpringJpaProjectionPosition> saved =
                ArgumentCaptor.forClass(SpringJpaProjectionPosition.class);
        verify(jpaRepository).save(saved.capture());
        assertThat(saved.getValue().getStreamId()).isEqualTo("missing");
        assertThat(saved.getValue().getNextEventNumber()).isZero();
    }
}
