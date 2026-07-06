package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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

    private InMemoryProjectionPositionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectionPositionRepository();
    }

    @Test
    void findByStreamId_未保存过_应返回Optional空() {
        Optional<ProjectionPosition> found = repository.findByStreamId("person-list");

        assertThat(found).isEmpty();
    }

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

    @Test
    void save_同streamId再次保存_应覆盖原值() {
        repository.save(new DefaultProjectionPosition("person-list", 5));

        repository.save(new DefaultProjectionPosition("person-list", 12));

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(12L);
    }

    @Test
    void deleteByStreamId_已存在_应删除并使后续查找返回空() {
        repository.save(new DefaultProjectionPosition("person-list", 5));

        repository.deleteByStreamId("person-list");

        assertThat(repository.findByStreamId("person-list")).isEmpty();
    }

    @Test
    void deleteByStreamId_不存在_应静默不抛异常() {
        repository.deleteByStreamId("not-exist");

        assertThat(repository.size()).isZero();
    }

    @Test
    void resetToZero_已存在位置_应将nextEventNumber置零() {
        repository.save(new DefaultProjectionPosition("person-list", 12));

        repository.resetToZero("person-list");

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(0L);
    }

    @Test
    void resetToZero_不存在位置_应创建nextEventNumber为零的新记录() {
        repository.resetToZero("person-list");

        assertThat(repository.findByStreamId("person-list"))
                .get()
                .extracting(ProjectionPosition::getNextEventNumber)
                .isEqualTo(0L);
    }

    @Test
    void findAll_多条记录_应返回全部且为不可变副本() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        List<ProjectionPosition> all = repository.findAll();

        assertThat(all).hasSize(2);
        assertThatThrownBy(() -> all.add(new DefaultProjectionPosition("c", 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshot_应返回当前存储的不可变副本() {
        repository.save(new DefaultProjectionPosition("a", 1));

        var snapshot = repository.snapshot();

        repository.save(new DefaultProjectionPosition("b", 2));

        assertThat(snapshot).hasSize(1).containsKey("a");
    }

    @Test
    void clear_应清空所有记录() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        repository.clear();

        assertThat(repository.size()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void size_应返回当前存储条目数() {
        repository.save(new DefaultProjectionPosition("a", 1));
        repository.save(new DefaultProjectionPosition("b", 2));

        assertThat(repository.size()).isEqualTo(2);
    }
}
