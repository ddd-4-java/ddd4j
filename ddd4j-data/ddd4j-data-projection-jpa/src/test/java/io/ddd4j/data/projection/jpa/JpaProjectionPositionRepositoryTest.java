package io.ddd4j.data.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>JpaProjectionPositionRepository</code> \u5728<code>findAll</code>\u3001<code>incrementByReportsTheStream</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class JpaProjectionPositionRepositoryTest {

    /** \u9a8c\u8bc1<code>positions.get(0).getNextEventNumber()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>positions.add(positions.get(0))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>positions.incrementBy("order-summary", 1L)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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
