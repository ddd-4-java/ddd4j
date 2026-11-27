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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>GuiceInMemoryProjectionPositionRepository</code> 在<code>findAll</code>等操作中的行为契约。
 */
class GuiceInMemoryProjectionPositionRepositoryTest {

    /**
     * 用于当前读取或写入断言的测试仓储。
     */
    private final GuiceInMemoryProjectionPositionRepository repository =
            new GuiceInMemoryProjectionPositionRepository();

    /**
     * 验证<code>saved</code> 与…为同一实例 <code>position</code>，<code>found.isPresent()</code>为真，<code>repository.findByStreamId("missing").isPresent()</code>为假。
     */
    @Test
    void saveAndFindByStreamId() {
        GuiceProjectionPosition position = new GuiceProjectionPosition("stream-1", 5L);

        ProjectionPosition saved = repository.save(position);

        assertSame(position, saved);
        Optional<ProjectionPosition> found = repository.findByStreamId("stream-1");
        assertTrue(found.isPresent());
        assertEquals(5L, found.get().getNextEventNumber());
        assertFalse(repository.findByStreamId("missing").isPresent());
    }

    /**
     * 验证<code>all.size()</code> 等于 <code>2</code>，<code>repository.size()</code> 等于 <code>2</code>，<code>all.add(new GuiceProjectionPosition("stream-3", 3L))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void findAllReturnsSnapshotOfStore() {
        repository.save(new GuiceProjectionPosition("stream-1", 1L));
        repository.save(new GuiceProjectionPosition("stream-2", 2L));

        List<ProjectionPosition> all = repository.findAll();

        assertEquals(2, all.size());
        assertEquals(2, repository.size());
        repository.clear();
        assertEquals(2, all.size());
        assertThrows(UnsupportedOperationException.class,
                () -> all.add(new GuiceProjectionPosition("stream-3", 3L)));
    }

    /**
     * 验证<code>repository.findByStreamId("stream-1").isPresent()</code>为假，<code>repository.size()</code> 等于 <code>0</code>。
     */
    @Test
    void deleteByStreamIdRemovesPosition() {
        repository.save(new GuiceProjectionPosition("stream-1", 1L));

        repository.deleteByStreamId("stream-1");

        assertFalse(repository.findByStreamId("stream-1").isPresent());
        assertEquals(0, repository.size());
    }

    /**
     * 验证<code>repository.findByStreamId("stream-1").get().getNextEventNumber()</code> 等于 <code>0L</code>，<code>repository.size()</code> 等于 <code>1</code>。
     */
    @Test
    void resetToZeroResetsExistingPositionOnly() {
        repository.save(new GuiceProjectionPosition("stream-1", 9L));

        repository.resetToZero("stream-1");
        assertEquals(0L, repository.findByStreamId("stream-1").get().getNextEventNumber());

        repository.resetToZero("stream-missing");
        assertEquals(1, repository.size());
    }

    /**
     * 验证<code>snapshot.size()</code> 等于 <code>1</code>，<code>repository.size()</code> 等于 <code>0</code>，<code>snapshot.put("stream-2", new GuiceProjectionPosition("stream-2", 2L))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void clearAndSnapshot() {
        repository.save(new GuiceProjectionPosition("stream-1", 1L));

        Map<String, ProjectionPosition> snapshot = repository.snapshot();
        assertEquals(1, snapshot.size());

        repository.clear();
        assertEquals(0, repository.size());
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.containsKey("stream-1"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("stream-2", new GuiceProjectionPosition("stream-2", 2L)));
    }
}
