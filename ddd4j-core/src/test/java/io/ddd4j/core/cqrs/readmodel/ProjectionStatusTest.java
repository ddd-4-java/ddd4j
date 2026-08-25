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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ProjectionStatus} record 契约测试 + {@link ViewManager#getProjectionStatus(String)} 默认行为测试。
 *
 * @author PartMe.AI
 */
@DisplayName("ProjectionStatus")
class ProjectionStatusTest {

    @Nested
    @DisplayName("record 构造与校验")
    class RecordConstruction {

        @Test
        void 正常构造_所有字段应正确赋值() {
            Instant now = Instant.now();
            ProjectionStatus status = new ProjectionStatus(
                    "orders", 42L, true, now, 10, null
            );

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(42L);
            assertThat(status.running()).isTrue();
            assertThat(status.lastRunAt()).isEqualTo(now);
            assertThat(status.lastEventCount()).isEqualTo(10);
            assertThat(status.lastError()).isNull();
        }

        @Test
        void streamId为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionStatus(
                    null, 0, false, null, 0, null
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        void nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new ProjectionStatus(
                    "orders", -1, false, null, 0, null
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lastEventCount为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new ProjectionStatus(
                    "orders", 0, false, null, -1, null
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lastError可为非null_记录失败信息() {
            ProjectionStatus status = new ProjectionStatus(
                    "orders", 5, false, Instant.now(), 3, "connection timeout"
            );

            assertThat(status.lastError()).isEqualTo("connection timeout");
        }
    }

    @Nested
    @DisplayName("baseline 工厂方法")
    class BaselineFactory {

        @Test
        void baseline_running为true_应返回正确状态() {
            ProjectionStatus status = ProjectionStatus.baseline("orders", true);

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(0L);
            assertThat(status.running()).isTrue();
            assertThat(status.lastRunAt()).isNull();
            assertThat(status.lastEventCount()).isEqualTo(0);
            assertThat(status.lastError()).isNull();
        }

        @Test
        void baseline_running为false_应返回正确状态() {
            ProjectionStatus status = ProjectionStatus.baseline("orders", false);

            assertThat(status.running()).isFalse();
        }
    }

    @Nested
    @DisplayName("不可变性与 equals/hashCode")
    class Immutability {

        @Test
        void 相同字段值_应equals且hashCode相同() {
            Instant now = Instant.now();
            ProjectionStatus a = new ProjectionStatus("orders", 5, true, now, 3, null);
            ProjectionStatus b = new ProjectionStatus("orders", 5, true, now, 3, null);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void 不同字段值_应不equals() {
            ProjectionStatus a = ProjectionStatus.baseline("orders", true);
            ProjectionStatus b = ProjectionStatus.baseline("orders", false);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("ViewManager.getProjectionStatus() 默认行为")
    class ViewManagerDefaultBehavior {

        @Test
        void default实现_running为true_应透传isRunning() {
            ViewManager manager = new ViewManager() {
                @Override
                public void start() { }

                @Override
                public void stop() { }

                @Override
                public boolean isRunning() {
                    return true;
                }

                @Override
                public void triggerOnce() { }
            };

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(0L);
            assertThat(status.running()).isTrue();
            assertThat(status.lastRunAt()).isNull();
            assertThat(status.lastEventCount()).isEqualTo(0);
            assertThat(status.lastError()).isNull();
        }

        @Test
        void default实现_running为false_应透传isRunning() {
            ViewManager manager = new ViewManager() {
                @Override
                public void start() { }

                @Override
                public void stop() { }

                @Override
                public boolean isRunning() {
                    return false;
                }

                @Override
                public void triggerOnce() { }
            };

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.running()).isFalse();
        }
    }
}
