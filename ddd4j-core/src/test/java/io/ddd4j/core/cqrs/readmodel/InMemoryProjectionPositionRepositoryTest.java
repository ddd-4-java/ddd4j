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
package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryProjectionPositionRepository} 单元测试。
 *
 * <p>覆盖存储读写、更新覆盖、删除、重置、findAll 拷贝语义以及 size/snapshot/clear 辅助方法。
 *
 * @author PartMe.AI
 */
@DisplayName("InMemoryProjectionPositionRepository")
class InMemoryProjectionPositionRepositoryTest {

    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    private InMemoryProjectionPositionRepository repository;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>repository = new InMemoryProjectionPositionRepository()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectionPositionRepository();
    }

    /** \u9a8c\u8bc1<code>found</code>\u4e3a\u7a7a\u3002 */
    @Test
    void findByStreamId_未保存过_应返回Optional空() {
        Optional<ProjectionPosition> found = repository.findByStreamId("person-list");

        assertThat(found).isEmpty();
    }

    /** \u9a8c\u8bc1<code>saved</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>position</code>\u3002 */
    @Test
    void save_新位置_应持久化并可按streamId查回() {
        ProjectionPosition position = new DefaultProjectionPosition("person-list", 5);

        ProjectionPosition saved = repository.save(position);

        assertThat(saved).isSameAs(position);
        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(5L);
    }

    /** \u9a8c\u8bc1<code>repository.findByStreamId("person-list")</code>\u4e2d\u7531 <code>ProjectionPosition::getNextEventNumber</code> \u8bfb\u53d6\u7684\u5c5e\u6027\u7b49\u4e8e <code>12L</code>\u3002 */
    @Test
    void save_同streamId再次保存_应覆盖原值() {
        repository.save(new DefaultProjectionPosition("person-list", 5));

        repository.save(new DefaultProjectionPosition("person-list", 12));

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(12L);
    }

    /** \u9a8c\u8bc1<code>repository.findByStreamId("person-list")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void deleteByStreamId_已存在_应删除并使后续查找返回空() {
        repository.save(new DefaultProjectionPosition("person-list", 5));

        repository.deleteByStreamId("person-list");

        assertThat(repository.findByStreamId("person-list")).isEmpty();
    }

    /** \u9a8c\u8bc1<code>repository.size()</code>\u4e3a\u96f6\u3002 */
    @Test
    void deleteByStreamId_不存在_应静默不抛异常() {
        repository.deleteByStreamId("not-exist");

        assertThat(repository.size()).isZero();
    }

    /** \u9a8c\u8bc1<code>repository.findByStreamId("person-list")</code>\u4e2d\u7531 <code>ProjectionPosition::getNextEventNumber</code> \u8bfb\u53d6\u7684\u5c5e\u6027\u7b49\u4e8e <code>0L</code>\u3002 */
    @Test
    void resetToZero_已存在位置_应将nextEventNumber置零() {
        repository.save(new DefaultProjectionPosition("person-list", 12));

        repository.resetToZero("person-list");

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(0L);
    }

    /** \u9a8c\u8bc1<code>repository.findByStreamId("person-list")</code>\u4e2d\u7531 <code>ProjectionPosition::getNextEventNumber</code> \u8bfb\u53d6\u7684\u5c5e\u6027\u7b49\u4e8e <code>0L</code>\u3002 */
    @Test
    void resetToZero_不存在位置_应创建nextEventNumber为零的新记录() {
        repository.resetToZero("person-list");

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(0L);
    }

    /** \u9a8c\u8bc1<code>all</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>all.add(new DefaultProjectionPosition("c", 3))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void findAll_多条记录_应返回全部且为不可变副本() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        List<ProjectionPosition> all = repository.findAll();

        repository.save(new DefaultProjectionPosition("c", 3));

        assertThat(all).hasSize(2);
        assertThatThrownBy(() -> all.add(new DefaultProjectionPosition("c", 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** \u9a8c\u8bc1<code>snapshot</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>snapshot.put("c", new DefaultProjectionPosition("c", 3))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void snapshot_应返回当前存储的不可变副本() {
        repository.save(new DefaultProjectionPosition("a", 1));

        Map<String, ProjectionPosition> snapshot = repository.snapshot();

        repository.save(new DefaultProjectionPosition("b", 2));

        assertThat(snapshot).hasSize(1).containsKey("a");
        assertThatThrownBy(() -> snapshot.put("c", new DefaultProjectionPosition("c", 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** \u9a8c\u8bc1<code>repository.size()</code>\u4e3a\u96f6\uff0c<code>repository.findAll()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void clear_应清空所有记录() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        repository.clear();

        assertThat(repository.size()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    /** \u9a8c\u8bc1<code>repository.size()</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void size_应返回当前存储条目数() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        assertThat(repository.size()).isEqualTo(2);
    }
}
