package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultProjectionService} 单元测试。
 *
 * <p>混合使用：
 * <ul>
 *   <li>真实 {@link InMemoryProjectionPositionRepository} —— 端到端行为校验</li>
 *   <li>Mock {@link ProjectionPositionRepository} —— 验证与仓储 SPI 的交互契约</li>
 * </ul>
 *
 * @author PartMe.AI
 */
@DisplayName("DefaultProjectionService")
@ExtendWith(MockitoExtension.class)
class DefaultProjectionServiceTest {

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        void 构造器_repository为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new DefaultProjectionService(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("repository must not be null");
        }
    }

    @Nested
    @DisplayName("readProjectionPosition")
    class ReadProjectionPosition {

        @Test
        void readProjectionPosition_位置不存在_应返回零() {
            DefaultProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());

            assertThat(service.readProjectionPosition("person-list")).isZero();
        }

        @Test
        void readProjectionPosition_位置存在_应返回已保存的事件号() {
            InMemoryProjectionPositionRepository repo = new InMemoryProjectionPositionRepository();
            repo.save(new DefaultProjectionPosition("person-list", 7));
            DefaultProjectionService service = new DefaultProjectionService(repo);

            assertThat(service.readProjectionPosition("person-list")).isEqualTo(7L);
        }

        @Test
        void readProjectionPosition_应委托仓储findByStreamId(@Mock ProjectionPositionRepository repo) {
            when(repo.findByStreamId("person-list")).thenReturn(Optional.empty());
            DefaultProjectionService service = new DefaultProjectionService(repo);

            long position = service.readProjectionPosition("person-list");

            assertThat(position).isZero();
            verify(repo, times(1)).findByStreamId("person-list");
        }
    }

    @Nested
    @DisplayName("updateProjectionPosition")
    class UpdateProjectionPosition {

        @Test
        void updateProjectionPosition_位置不存在_应创建新位置并保存() {
            InMemoryProjectionPositionRepository repo = new InMemoryProjectionPositionRepository();
            DefaultProjectionService service = new DefaultProjectionService(repo);

            ProjectionPosition saved = service.updateProjectionPosition("person-list", 12);

            assertThat(saved.getStreamId()).isEqualTo("person-list");
            assertThat(saved.getNextEventNumber()).isEqualTo(12L);
            assertThat(service.readProjectionPosition("person-list")).isEqualTo(12L);
        }

        @Test
        void updateProjectionPosition_位置已存在_应基于现有位置推进() {
            InMemoryProjectionPositionRepository repo = new InMemoryProjectionPositionRepository();
            repo.save(new DefaultProjectionPosition("person-list", 5));
            DefaultProjectionService service = new DefaultProjectionService(repo);

            ProjectionPosition saved = service.updateProjectionPosition("person-list", 9);

            assertThat(saved.getNextEventNumber()).isEqualTo(9L);
            assertThat(service.readProjectionPosition("person-list")).isEqualTo(9L);
        }

        @Test
        void updateProjectionPosition_位置已存在_应调用withNextEventNumber基于现有实例(@Mock ProjectionPositionRepository repo,
                                                                                       @Mock ProjectionPosition current) {
            ProjectionPosition advanced = new DefaultProjectionPosition("person-list", 9);
            when(current.withNextEventNumber(9L)).thenReturn(advanced);
            when(repo.findByStreamId("person-list")).thenReturn(Optional.of(current));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DefaultProjectionService service = new DefaultProjectionService(repo);

            ProjectionPosition saved = service.updateProjectionPosition("person-list", 9);

            assertThat(saved).isSameAs(advanced);
            verify(current, times(1)).withNextEventNumber(9L);
            verify(repo, times(1)).save(advanced);
        }

        @Test
        void updateProjectionPosition_nextEventNumber为负_应抛IllegalArgumentException() {
            DefaultProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());

            assertThatThrownBy(() -> service.updateProjectionPosition("person-list", -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nextEventNumber must not be negative");
        }

        @Test
        void updateProjectionPosition_nextEventNumber为零_应允许(@Mock ProjectionPositionRepository repo,
                                                            @Mock ProjectionPosition current) {
            ProjectionPosition zeroed = new DefaultProjectionPosition("person-list", 0);
            when(current.withNextEventNumber(0L)).thenReturn(zeroed);
            when(repo.findByStreamId("person-list")).thenReturn(Optional.of(current));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DefaultProjectionService service = new DefaultProjectionService(repo);

            ProjectionPosition saved = service.updateProjectionPosition("person-list", 0);

            assertThat(saved.getNextEventNumber()).isZero();
            verify(repo).save(zeroed);
        }
    }

    @Nested
    @DisplayName("resetProjectionPosition")
    class ResetProjectionPosition {

        @Test
        void resetProjectionPosition_应委托仓储resetToZero(@Mock ProjectionPositionRepository repo) {
            DefaultProjectionService service = new DefaultProjectionService(repo);

            service.resetProjectionPosition("person-list");

            verify(repo, times(1)).resetToZero(eq("person-list"));
            verify(repo, never()).save(any());
        }

        @Test
        void resetProjectionPosition_应使后续读取返回零() {
            InMemoryProjectionPositionRepository repo = new InMemoryProjectionPositionRepository();
            repo.save(new DefaultProjectionPosition("person-list", 12));
            DefaultProjectionService service = new DefaultProjectionService(repo);

            service.resetProjectionPosition("person-list");

            assertThat(service.readProjectionPosition("person-list")).isZero();
        }

        @Test
        void resetProjectionPosition_对不存在的streamId_应仅调用resetToZero不触发save(@Mock ProjectionPositionRepository repo) {
            DefaultProjectionService service = new DefaultProjectionService(repo);

            service.resetProjectionPosition("not-exist");

            verify(repo).resetToZero("not-exist");
            verifyNoInteractionsBeyondReset(repo);
        }

        private void verifyNoInteractionsBeyondReset(ProjectionPositionRepository repo) {
            // resetToZero 是唯一应被调用的方法
            verify(repo, never()).save(any());
            verify(repo, never()).findByStreamId(any());
        }
    }
}
