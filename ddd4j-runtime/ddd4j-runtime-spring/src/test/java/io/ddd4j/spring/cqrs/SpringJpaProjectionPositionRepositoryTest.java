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

/** \u9a8c\u8bc1 <code>SpringJpaProjectionPositionRepository</code> \u5728<code>findAll</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class SpringJpaProjectionPositionRepositoryTest {

    /** \u7528\u4e8e\u9694\u79bb <code>JpaRepository&lt;SpringJpaProjectionPosition, String&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private JpaRepository<SpringJpaProjectionPosition, String> jpaRepository;

    /** \u9a8c\u8bc1<code>positions.add(positions.get(0))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>saved.getValue().getStreamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>saved.getValue().getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
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

    /** \u9a8c\u8bc1<code>saved.getValue().getStreamId()</code> \u7b49\u4e8e <code>"missing"</code>\uff0c<code>saved.getValue().getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
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
