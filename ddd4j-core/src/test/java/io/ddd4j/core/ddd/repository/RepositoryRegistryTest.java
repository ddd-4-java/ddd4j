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
package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryRegistryTest {

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        RepositoryRegistry.clear();
        BaseContext.clear();
    }

    @Test
    void shouldRegisterResolveAndUnregisterRepository() {
        InMemoryRepository repository = new InMemoryRepository();
        RepositoryRegistry.register(TestAggregate.class, repository);
        assertSame(repository, RepositoryRegistry.repository(TestAggregate.class));
        TestAggregate aggregate = new TestAggregate(new StringEntityId("a-1"));
        repository.save(aggregate);
        assertEquals(aggregate, RepositoryRegistry.repository(TestAggregate.class).findById(aggregate.id()).get());
        RepositoryRegistry.unregister(TestAggregate.class);
        assertThrows(BizRuntimeException.class, () -> RepositoryRegistry.repository(TestAggregate.class));
    }

    @Test
    void threadContext_shouldOverrideBaseContext() {
        InMemoryRepository globalRepository = new InMemoryRepository();
        InMemoryRepository threadRepository = new InMemoryRepository();
        RepositoryRegistry.register(TestAggregate.class, globalRepository);
        ThreadContext.inject(RepositoryRegistry.key(TestAggregate.class), Repository.class, threadRepository);

        assertSame(threadRepository, RepositoryRegistry.repository(TestAggregate.class));
    }

    @Test
    void repository_shouldFallbackToBaseContextAfterThreadRemoved() {
        InMemoryRepository globalRepository = new InMemoryRepository();
        InMemoryRepository threadRepository = new InMemoryRepository();
        RepositoryRegistry.register(TestAggregate.class, globalRepository);
        ThreadContext.inject(RepositoryRegistry.key(TestAggregate.class), Repository.class, threadRepository);
        assertSame(threadRepository, RepositoryRegistry.repository(TestAggregate.class));

        ThreadContext.clear();

        assertSame(globalRepository, RepositoryRegistry.repository(TestAggregate.class));
    }

    @Test
    void registerWithQuery_shouldResolveBothAggregateAndQuery() {
        InMemoryRepository repository = new InMemoryRepository();
        RepositoryRegistry.register(TestAggregate.class, TestQuery.class, repository);

        assertSame(repository, RepositoryRegistry.repository(TestAggregate.class));
        assertSame(repository, RepositoryRegistry.repositoryForQuery(TestQuery.class));
    }

    @Test
    void unregisterQuery_shouldRemoveQueryMapping() {
        RepositoryRegistry.register(TestAggregate.class, TestQuery.class, new InMemoryRepository());
        RepositoryRegistry.unregisterQuery(TestQuery.class);

        assertThrows(BizRuntimeException.class, () -> RepositoryRegistry.repositoryForQuery(TestQuery.class));
    }

    @Test
    void clear_shouldRemoveAllRegistrations() {
        RepositoryRegistry.register(TestAggregate.class, TestQuery.class, new InMemoryRepository());
        RepositoryRegistry.clear();

        assertThrows(BizRuntimeException.class, () -> RepositoryRegistry.repository(TestAggregate.class));
        assertThrows(BizRuntimeException.class, () -> RepositoryRegistry.repositoryForQuery(TestQuery.class));
    }

    private static final class TestQuery extends Query<TestAggregate> {
    }

    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        private final StringEntityId id;

        private TestAggregate(StringEntityId id) {
            this.id = id;
        }

        @Override
        public StringEntityId id() {
            return id;
        }
    }

    private static final class InMemoryRepository implements Repository<TestAggregate, StringEntityId> {
        private final Map<StringEntityId, TestAggregate> values = new HashMap<StringEntityId, TestAggregate>();

        @Override
        public Optional<TestAggregate> findById(StringEntityId id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public TestAggregate save(TestAggregate aggregate) {
            values.put(aggregate.id(), aggregate);
            return aggregate;
        }
    }
}
