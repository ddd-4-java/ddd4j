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
import javax.enterprise.inject.Instance;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 验证投影位置查询返回可修改的独立结果快照。 */
@ExtendWith(MockitoExtension.class)
class QuarkusJpaProjectionPositionRepositoryListTest {

    @Mock
    Instance<EntityManager> entityManagers;

    @Mock
    EntityManager manager;

    @Mock
    TypedQuery<QuarkusJpaProjectionPosition> query;

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
}
