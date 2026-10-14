package io.ddd4j.data.projection.jpa;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaProjectionPositionRepositoryTest {

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
