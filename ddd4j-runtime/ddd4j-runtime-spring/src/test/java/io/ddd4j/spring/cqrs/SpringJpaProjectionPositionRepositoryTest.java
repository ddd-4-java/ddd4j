/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.spring.cqrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringJpaProjectionPositionRepositoryTest {

    @Mock
    private JpaRepository<SpringJpaProjectionPosition, String> jpaRepository;

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
