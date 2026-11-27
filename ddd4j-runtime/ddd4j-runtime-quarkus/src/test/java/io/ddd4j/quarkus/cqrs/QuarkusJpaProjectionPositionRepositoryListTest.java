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

    /** \u7528\u4e8e\u9694\u79bb <code>Instance&lt;EntityManager&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    Instance<EntityManager> entityManagers;

    /** \u7528\u4e8e\u9694\u79bb <code>EntityManager</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    EntityManager manager;

    /** \u7528\u4e8e\u9694\u79bb <code>TypedQuery&lt;QuarkusJpaProjectionPosition&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    TypedQuery<QuarkusJpaProjectionPosition> query;

    /** \u9a8c\u8bc1<code>snapshot</code>\u4e3a\u7a7a\uff0c<code>repository.findAll()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>repository.save(newPosition)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>newPosition</code>\uff0c<code>repository.save(newPosition)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>managed</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>entityManagers.get()</code>\u3001<code>mock()</code>\u3001<code>manager.createQuery()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>entityManagers.get()</code>\u3001<code>repository.deleteByStreamId()</code>\u3001<code>never()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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
