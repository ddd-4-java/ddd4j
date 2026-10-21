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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.constant.ProjectionConstants;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuarkusJpaProjectionPosition} 不可变契约与字段封装测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class QuarkusJpaProjectionPositionTest {

    @Test
    void usesTheJpaNamespaceAndColumnsSupportedByThisJdk() throws Exception {
        String namespace = "1.8".equals(System.getProperty("java.specification.version"))
                ? "javax.persistence." : "jakarta.persistence.";
        assertThat(annotation(QuarkusJpaProjectionPosition.class, namespace + "Entity")).isNotNull();
        Annotation table = annotation(QuarkusJpaProjectionPosition.class, namespace + "Table");
        assertThat(table).isNotNull();
        assertThat(table.annotationType().getMethod("name").invoke(table))
                .isEqualTo(ProjectionConstants.TABLE_NAME);
        Annotation streamIdColumn = annotation(
                QuarkusJpaProjectionPosition.class.getDeclaredField("streamId"), namespace + "Column");
        Annotation nextEventColumn = annotation(
                QuarkusJpaProjectionPosition.class.getDeclaredField("nextEventNumber"), namespace + "Column");
        assertThat(streamIdColumn).isNotNull();
        assertThat(nextEventColumn).isNotNull();
        assertThat(streamIdColumn.annotationType().getMethod("name").invoke(streamIdColumn))
                .isEqualTo(ProjectionConstants.COLUMN_STREAM_ID);
        assertThat(nextEventColumn.annotationType().getMethod("name").invoke(nextEventColumn))
                .isEqualTo(ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER);
    }

    private static Annotation annotation(java.lang.reflect.AnnotatedElement element, String typeName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (typeName.equals(annotation.annotationType().getName())) {
                return annotation;
            }
        }
        return null;
    }

    @Test
    void withNextEventNumberShouldReturnNewInstance() {
        QuarkusJpaProjectionPosition original = new QuarkusJpaProjectionPosition("stream-1", 10L);

        ProjectionPosition advanced = original.withNextEventNumber(42L);

        assertThat(advanced).isNotSameAs(original);
        assertThat(advanced).isInstanceOf(QuarkusJpaProjectionPosition.class);
    }

    @Test
    void withNextEventNumberShouldNotModifyOriginal() {
        QuarkusJpaProjectionPosition original = new QuarkusJpaProjectionPosition("stream-1", 10L);

        original.withNextEventNumber(42L);

        assertThat(original.getStreamId()).isEqualTo("stream-1");
        assertThat(original.getNextEventNumber()).isEqualTo(10L);
    }

    @Test
    void withNextEventNumberShouldPreserveStreamId() {
        QuarkusJpaProjectionPosition original = new QuarkusJpaProjectionPosition("stream-abc", 5L);

        ProjectionPosition advanced = original.withNextEventNumber(99L);

        assertThat(advanced.getStreamId()).isEqualTo("stream-abc");
        assertThat(advanced.getNextEventNumber()).isEqualTo(99L);
    }

    @Test
    void fieldsShouldBePrivate() throws NoSuchFieldException {
        assertThat(Modifier.isPrivate(QuarkusJpaProjectionPosition.class.getDeclaredField("streamId").getModifiers()))
                .as("streamId 字段应为 private").isTrue();
        assertThat(Modifier.isPrivate(QuarkusJpaProjectionPosition.class.getDeclaredField("nextEventNumber").getModifiers()))
                .as("nextEventNumber 字段应为 private").isTrue();
    }

    @Test
    void gettersShouldReturnCorrectValues() {
        QuarkusJpaProjectionPosition position = new QuarkusJpaProjectionPosition("stream-X", 77L);

        assertThat(position.getStreamId()).isEqualTo("stream-X");
        assertThat(position.getNextEventNumber()).isEqualTo(77L);
    }

    @Test
    void noArgConstructorShouldCreateEmptyInstance() {
        QuarkusJpaProjectionPosition position = new QuarkusJpaProjectionPosition();

        assertThat(position.getStreamId()).isNull();
        assertThat(position.getNextEventNumber()).isEqualTo(0L);
    }
}
