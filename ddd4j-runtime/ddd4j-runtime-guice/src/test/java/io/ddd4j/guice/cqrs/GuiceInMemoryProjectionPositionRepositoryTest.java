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

/** \u9a8c\u8bc1 <code>GuiceInMemoryProjectionPositionRepository</code> \u5728<code>findAll</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class GuiceInMemoryProjectionPositionRepositoryTest {

    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    private final GuiceInMemoryProjectionPositionRepository repository =
            new GuiceInMemoryProjectionPositionRepository();

    /** \u9a8c\u8bc1<code>saved</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>position</code>\uff0c<code>found.isPresent()</code>\u4e3a\u771f\uff0c<code>repository.findByStreamId("missing").isPresent()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>all.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>repository.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>all.add(new GuiceProjectionPosition("stream-3", 3L))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>repository.findByStreamId("stream-1").isPresent()</code>\u4e3a\u5047\uff0c<code>repository.size()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void deleteByStreamIdRemovesPosition() {
        repository.save(new GuiceProjectionPosition("stream-1", 1L));

        repository.deleteByStreamId("stream-1");

        assertFalse(repository.findByStreamId("stream-1").isPresent());
        assertEquals(0, repository.size());
    }

    /** \u9a8c\u8bc1<code>repository.findByStreamId("stream-1").get().getNextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>repository.size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void resetToZeroResetsExistingPositionOnly() {
        repository.save(new GuiceProjectionPosition("stream-1", 9L));

        repository.resetToZero("stream-1");
        assertEquals(0L, repository.findByStreamId("stream-1").get().getNextEventNumber());

        repository.resetToZero("stream-missing");
        assertEquals(1, repository.size());
    }

    /** \u9a8c\u8bc1<code>snapshot.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>repository.size()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>snapshot.put("stream-2", new GuiceProjectionPosition("stream-2", 2L))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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
