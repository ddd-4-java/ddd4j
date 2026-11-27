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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证投影位置查询返回可修改的独立结果快照。
 */
@ExtendWith(MockitoExtension.class)
class QuarkusJpaProjectionPositionRepositoryListTest {

    /**
     * 用于隔离 <code>Instance&lt;EntityManager&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    Instance<EntityManager> entityManagers;

    /**
     * 用于隔离 <code>EntityManager</code> 调用并记录交互的测试替身。
     */
    @Mock
    EntityManager manager;

    /**
     * 用于隔离 <code>TypedQuery&lt;QuarkusJpaProjectionPosition&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    TypedQuery<QuarkusJpaProjectionPosition> query;

    /**
     * 验证<code>snapshot</code>为空，<code>repository.findAll()</code> 的大小为 <code>1</code>。
     */
    @Test
    void findAllReturnsMutableDetachedList() {
        when(entityManagers.get()).thenReturn(manager);
        when(manager.createQuery(anyString(), eq(QuarkusJpaProjectionPosition.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(
                Collections.singletonList(new QuarkusJpaProjectionPosition("orders", 7L)));
        QuarkusJpaProjectionPositionRepository repository = new QuarkusJpaProjectionPositionRepository();
        repository.entityManagers = entityManagers;

        List<ProjectionPosition> snapshot = repository.findAll();
        snapshot.clear();

        assertThat(snapshot).isEmpty();
        assertThat(repository.findAll()).hasSize(1);
    }

    /**
     * 验证<code>repository.save(newPosition)</code> 指向同一实例 <code>newPosition</code>，<code>repository.save(newPosition)</code> 指向同一实例 <code>managed</code>。
     */
    @Test
    void saveKeepsLegacyEntityIdentityAndMergeResult() {
        when(entityManagers.get()).thenReturn(manager);
        QuarkusJpaProjectionPositionRepository repository = new QuarkusJpaProjectionPositionRepository();
        repository.entityManagers = entityManagers;
        QuarkusJpaProjectionPosition newPosition = new QuarkusJpaProjectionPosition("orders", 7L);
        assertThat(repository.save(newPosition)).isSameAs(newPosition);
        verify(manager).persist(newPosition);
        QuarkusJpaProjectionPosition managed = new QuarkusJpaProjectionPosition("orders", 9L);
        when(manager.find(QuarkusJpaProjectionPosition.class, "orders")).thenReturn(managed);
        when(manager.merge(newPosition)).thenReturn(managed);
        assertThat(repository.save(newPosition)).isSameAs(managed);
    }

    /**
     * 在测试框架中执行 <code>entityManagers.get()</code>、<code>mock()</code>、<code>manager.createQuery()</code>，验证该场景。
     */
    @Test
    void missingResetRetainsUpdateOnlySemantics() {
        when(entityManagers.get()).thenReturn(manager);
        Query update = mock(Query.class);
        when(manager.createQuery("update QuarkusJpaProjectionPosition p set p.nextEventNumber = 0 "
                + "where p.streamId = :streamId")).thenReturn(update);
        when(update.setParameter("streamId", "missing")).thenReturn(update);
        QuarkusJpaProjectionPositionRepository repository = new QuarkusJpaProjectionPositionRepository();
        repository.entityManagers = entityManagers;
        repository.resetToZero("missing");
        verify(update).executeUpdate();
        verify(manager, never()).persist(any());
        verify(manager, never()).clear();
    }

    /**
     * 在测试框架中执行 <code>entityManagers.get()</code>、<code>repository.deleteByStreamId()</code>、<code>never()</code>，验证该场景。
     */
    @Test
    void deleteOnlyRemovesAnExistingEntity() {
        when(entityManagers.get()).thenReturn(manager);
        QuarkusJpaProjectionPositionRepository repository = new QuarkusJpaProjectionPositionRepository();
        repository.entityManagers = entityManagers;
        repository.deleteByStreamId("missing");
        verify(manager, never()).remove(any());
        QuarkusJpaProjectionPosition managed = new QuarkusJpaProjectionPosition("orders", 9L);
        when(manager.find(QuarkusJpaProjectionPosition.class, "orders")).thenReturn(managed);
        repository.deleteByStreamId("orders");
        verify(manager).remove(managed);
    }

}
