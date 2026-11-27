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

    /**
     * 用于当前读取或写入断言的测试仓储。
     */
    private InMemoryProjectionPositionRepository repository;

    /**
     * 根据本次调用更新 <code>repository = new InMemoryProjectionPositionRepository()</code>，用于后续测试步骤。
     */
    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectionPositionRepository();
    }

    /**
     * 验证<code>found</code>为空。
     */
    @Test
    void findByStreamId_未保存过_应返回Optional空() {
        Optional<ProjectionPosition> found = repository.findByStreamId("person-list");

        assertThat(found).isEmpty();
    }

    /**
     * 验证<code>saved</code> 指向同一实例 <code>position</code>。
     */
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

    /**
     * 验证<code>repository.findByStreamId("person-list")</code>中由 <code>ProjectionPosition::getNextEventNumber</code> 读取的属性等于 <code>12L</code>。
     */
    @Test
    void save_同streamId再次保存_应覆盖原值() {
        repository.save(new DefaultProjectionPosition("person-list", 5));

        repository.save(new DefaultProjectionPosition("person-list", 12));

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(12L);
    }

    /**
     * 验证<code>repository.findByStreamId("person-list")</code>为空。
     */
    @Test
    void deleteByStreamId_已存在_应删除并使后续查找返回空() {
        repository.save(new DefaultProjectionPosition("person-list", 5));

        repository.deleteByStreamId("person-list");

        assertThat(repository.findByStreamId("person-list")).isEmpty();
    }

    /**
     * 验证<code>repository.size()</code>为零。
     */
    @Test
    void deleteByStreamId_不存在_应静默不抛异常() {
        repository.deleteByStreamId("not-exist");

        assertThat(repository.size()).isZero();
    }

    /**
     * 验证<code>repository.findByStreamId("person-list")</code>中由 <code>ProjectionPosition::getNextEventNumber</code> 读取的属性等于 <code>0L</code>。
     */
    @Test
    void resetToZero_已存在位置_应将nextEventNumber置零() {
        repository.save(new DefaultProjectionPosition("person-list", 12));

        repository.resetToZero("person-list");

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(0L);
    }

    /**
     * 验证<code>repository.findByStreamId("person-list")</code>中由 <code>ProjectionPosition::getNextEventNumber</code> 读取的属性等于 <code>0L</code>。
     */
    @Test
    void resetToZero_不存在位置_应创建nextEventNumber为零的新记录() {
        repository.resetToZero("person-list");

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(0L);
    }

    /**
     * 验证<code>all</code> 的大小为 <code>2</code>，<code>all.add(new DefaultProjectionPosition("c", 3))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
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

    /**
     * 验证<code>snapshot</code> 的大小为 <code>1</code>，<code>snapshot.put("c", new DefaultProjectionPosition("c", 3))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void snapshot_应返回当前存储的不可变副本() {
        repository.save(new DefaultProjectionPosition("a", 1));

        Map<String, ProjectionPosition> snapshot = repository.snapshot();

        repository.save(new DefaultProjectionPosition("b", 2));

        assertThat(snapshot).hasSize(1).containsKey("a");
        assertThatThrownBy(() -> snapshot.put("c", new DefaultProjectionPosition("c", 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证<code>repository.size()</code>为零，<code>repository.findAll()</code>为空。
     */
    @Test
    void clear_应清空所有记录() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        repository.clear();

        assertThat(repository.size()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    /**
     * 验证<code>repository.size()</code> 等于 <code>2</code>。
     */
    @Test
    void size_应返回当前存储条目数() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        assertThat(repository.size()).isEqualTo(2);
    }
}
