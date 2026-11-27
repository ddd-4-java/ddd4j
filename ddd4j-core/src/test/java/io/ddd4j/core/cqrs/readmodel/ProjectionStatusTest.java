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
 * {@link ProjectionStatus} 值对象契约测试 + {@link ViewManager#getProjectionStatus(String)} 默认行为测试。
 *
 * @author PartMe.AI
 */
@DisplayName("ProjectionStatus")
class ProjectionStatusTest {

    /** \u8986\u76d6 <code>\u503c\u5bf9\u8c61\u6784\u9020\u4e0e\u6821\u9a8c</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("值对象构造与校验")
    class RecordConstruction {

        /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>42L</code>\uff0c<code>status.lastError()</code>\u4e3a\u7a7a\u3002 */
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

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void streamId为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new ProjectionStatus(
                    null, 0, false, null, 0, null
            )).isInstanceOf(NullPointerException.class);
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new ProjectionStatus(
                    "orders", -1, false, null, 0, null
            )).isInstanceOf(IllegalArgumentException.class);
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void lastEventCount为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new ProjectionStatus(
                    "orders", 0, false, null, -1, null
            )).isInstanceOf(IllegalArgumentException.class);
        }

        /** \u9a8c\u8bc1<code>status.lastError()</code> \u7b49\u4e8e <code>"connection timeout"</code>\u3002 */
        @Test
        void lastError可为非null_记录失败信息() {
            ProjectionStatus status = new ProjectionStatus(
                    "orders", 5, false, Instant.now(), 3, "connection timeout"
            );

            assertThat(status.lastError()).isEqualTo("connection timeout");
        }
    }

    /** \u8986\u76d6 <code>baseline \u5de5\u5382\u65b9\u6cd5</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("baseline 工厂方法")
    class BaselineFactory {

        /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>status.lastError()</code>\u4e3a\u7a7a\u3002 */
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

        /** \u9a8c\u8bc1<code>status.running()</code>\u4e3a\u5047\u3002 */
        @Test
        void baseline_running为false_应返回正确状态() {
            ProjectionStatus status = ProjectionStatus.baseline("orders", false);

            assertThat(status.running()).isFalse();
        }
    }

    /** \u8986\u76d6 <code>\u4e0d\u53ef\u53d8\u6027\u4e0e equals/hashCode</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("不可变性与 equals/hashCode")
    class Immutability {

        /** \u9a8c\u8bc1<code>a</code> \u7b49\u4e8e <code>b</code>\uff0c<code>a.hashCode()</code> \u7b49\u4e8e <code>b.hashCode()</code>\u3002 */
        @Test
        void 相同字段值_应equals且hashCode相同() {
            Instant now = Instant.now();
            ProjectionStatus a = new ProjectionStatus("orders", 5, true, now, 3, null);
            ProjectionStatus b = new ProjectionStatus("orders", 5, true, now, 3, null);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        /** \u9a8c\u8bc1<code>a</code> \u4e0d\u7b49\u4e8e <code>b</code>\u3002 */
        @Test
        void 不同字段值_应不equals() {
            ProjectionStatus a = ProjectionStatus.baseline("orders", true);
            ProjectionStatus b = ProjectionStatus.baseline("orders", false);

            assertThat(a).isNotEqualTo(b);
        }
    }

    /** \u8986\u76d6 <code>ViewManager.getProjectionStatus() \u9ed8\u8ba4\u884c\u4e3a</code> \u573a\u666f\u53ca\u5176\u9884\u671f\u884c\u4e3a\u3002 */
    @Nested
    @DisplayName("ViewManager.getProjectionStatus() 默认行为")
    class ViewManagerDefaultBehavior {

        /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>status.lastError()</code>\u4e3a\u7a7a\u3002 */
        @Test
        void default实现_running为true_应透传isRunning() {
            ViewManager manager = new ViewManager() {
                @Override
                public void start() {
                }

                @Override
                public void stop() {
                }

                @Override
                public boolean isRunning() {
                    return true;
                }

                @Override
                public void triggerOnce() {
                }
            };

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.streamId()).isEqualTo("orders");
            assertThat(status.nextEventNumber()).isEqualTo(0L);
            assertThat(status.running()).isTrue();
            assertThat(status.lastRunAt()).isNull();
            assertThat(status.lastEventCount()).isEqualTo(0);
            assertThat(status.lastError()).isNull();
        }

        /** \u9a8c\u8bc1<code>status.running()</code>\u4e3a\u5047\u3002 */
        @Test
        void default实现_running为false_应透传isRunning() {
            ViewManager manager = new ViewManager() {
                @Override
                public void start() {
                }

                @Override
                public void stop() {
                }

                @Override
                public boolean isRunning() {
                    return false;
                }

                @Override
                public void triggerOnce() {
                }
            };

            ProjectionStatus status = manager.getProjectionStatus("orders");

            assertThat(status.running()).isFalse();
        }
    }
}
