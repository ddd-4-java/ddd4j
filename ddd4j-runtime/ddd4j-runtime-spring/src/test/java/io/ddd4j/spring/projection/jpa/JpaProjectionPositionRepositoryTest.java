package io.ddd4j.spring.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 <code>JpaProjectionPositionRepository</code> 在<code>findAll</code>、<code>incrementByReportsTheStream</code>等操作中的行为契约。
 */
class JpaProjectionPositionRepositoryTest {

    /**
     * 验证<code>positions.get(0).getNextEventNumber()</code> 等于 <code>2L</code>，<code>positions.add(positions.get(0))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void findAllReturnsImmutablePositionValuesInRepositoryOrder() {
        SpringDataProjectionPositionRepository repository = mock(SpringDataProjectionPositionRepository.class);
        ProjectionPositionEntity first = new ProjectionPositionEntity("orders", 2L);
        ProjectionPositionEntity second = new ProjectionPositionEntity("payments", 5L);
        when(repository.findAll()).thenReturn(Arrays.asList(first, second));

        List<ProjectionPosition> positions =
                new JpaProjectionPositionRepository(repository).findAll();

        assertThat(positions).extracting("streamId").containsExactly("orders", "payments");
        assertThat(positions).extracting("nextEventNumber").containsExactly(2L, 5L);
        first.setNextEventNumber(9L);
        assertThat(positions.get(0).getNextEventNumber()).isEqualTo(2L);
        assertThatThrownBy(() -> positions.add(positions.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证<code>positions.incrementBy("order-summary", 1L)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void incrementByReportsTheStreamWhenUpdatedRowDisappearsBeforeReload() {
        SpringDataProjectionPositionRepository repository = mock(SpringDataProjectionPositionRepository.class);
        when(repository.incrementBy("order-summary", 1L)).thenReturn(1);
        when(repository.findById("order-summary")).thenReturn(Optional.empty());

        JpaProjectionPositionRepository positions = new JpaProjectionPositionRepository(repository);

        assertThatThrownBy(() -> positions.incrementBy("order-summary", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order-summary");
    }
}
