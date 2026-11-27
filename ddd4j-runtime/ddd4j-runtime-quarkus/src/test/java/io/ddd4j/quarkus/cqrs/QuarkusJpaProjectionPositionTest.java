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

import io.ddd4j.core.constant.ProjectionConstants;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
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

    /**
     * \u6309 <code>(typeName.equals(annotation.annotationType().getName()))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param element \u4f20\u7ed9 <code>element.getAnnotations()</code> \u7684 <code>java.lang.reflect.AnnotatedElement</code> \u8f93\u5165
     * @param typeName \u4f20\u7ed9 <code>typeName.equals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>annotation</code>\u3001<code>null</code>
     */
    private static Annotation annotation(java.lang.reflect.AnnotatedElement element, String typeName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (typeName.equals(annotation.annotationType().getName())) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * \u9a8c\u8bc1<code>annotation(QuarkusJpaProjectionPosition.class, namespace + "Entity")</code>\u975e\u7a7a\uff0c<code>table</code>\u975e\u7a7a\uff0c<code>nextEventColumn.annotationType().getMethod("name").invoke(nextEventColumn)</code> \u7b49\u4e8e <code>ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1<code>advanced</code> \u6307\u5411\u4e0d\u540c\u5b9e\u4f8b <code>original</code>\uff0c<code>advanced</code> \u7684\u7c7b\u578b\u4e3a <code>QuarkusJpaProjectionPosition.class</code>\u3002 */
    @Test
    void withNextEventNumberShouldReturnNewInstance() {
        QuarkusJpaProjectionPosition original = new QuarkusJpaProjectionPosition("stream-1", 10L);

        ProjectionPosition advanced = original.withNextEventNumber(42L);

        assertThat(advanced).isNotSameAs(original);
        assertThat(advanced).isInstanceOf(QuarkusJpaProjectionPosition.class);
    }

    /** \u9a8c\u8bc1<code>original.getStreamId()</code> \u7b49\u4e8e <code>"stream-1"</code>\uff0c<code>original.getNextEventNumber()</code> \u7b49\u4e8e <code>10L</code>\u3002 */
    @Test
    void withNextEventNumberShouldNotModifyOriginal() {
        QuarkusJpaProjectionPosition original = new QuarkusJpaProjectionPosition("stream-1", 10L);

        original.withNextEventNumber(42L);

        assertThat(original.getStreamId()).isEqualTo("stream-1");
        assertThat(original.getNextEventNumber()).isEqualTo(10L);
    }

    /** \u9a8c\u8bc1<code>advanced.getStreamId()</code> \u7b49\u4e8e <code>"stream-abc"</code>\uff0c<code>advanced.getNextEventNumber()</code> \u7b49\u4e8e <code>99L</code>\u3002 */
    @Test
    void withNextEventNumberShouldPreserveStreamId() {
        QuarkusJpaProjectionPosition original = new QuarkusJpaProjectionPosition("stream-abc", 5L);

        ProjectionPosition advanced = original.withNextEventNumber(99L);

        assertThat(advanced.getStreamId()).isEqualTo("stream-abc");
        assertThat(advanced.getNextEventNumber()).isEqualTo(99L);
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Modifier.isPrivate()</code>\u3001<code>QuarkusJpaProjectionPosition.class.getDeclaredField()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws NoSuchFieldException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u5b57\u6bb5\u5931\u8d25
     */
    @Test
    void fieldsShouldBePrivate() throws NoSuchFieldException {
        assertThat(Modifier.isPrivate(QuarkusJpaProjectionPosition.class.getDeclaredField("streamId").getModifiers()))
                .as("streamId 字段应为 private").isTrue();
        assertThat(Modifier.isPrivate(QuarkusJpaProjectionPosition.class.getDeclaredField("nextEventNumber").getModifiers()))
                .as("nextEventNumber 字段应为 private").isTrue();
    }

    /** \u9a8c\u8bc1<code>position.getStreamId()</code> \u7b49\u4e8e <code>"stream-X"</code>\uff0c<code>position.getNextEventNumber()</code> \u7b49\u4e8e <code>77L</code>\u3002 */
    @Test
    void gettersShouldReturnCorrectValues() {
        QuarkusJpaProjectionPosition position = new QuarkusJpaProjectionPosition("stream-X", 77L);

        assertThat(position.getStreamId()).isEqualTo("stream-X");
        assertThat(position.getNextEventNumber()).isEqualTo(77L);
    }

    /** \u9a8c\u8bc1<code>position.getStreamId()</code>\u4e3a\u7a7a\uff0c<code>position.getNextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\u3002 */
    @Test
    void noArgConstructorShouldCreateEmptyInstance() {
        QuarkusJpaProjectionPosition position = new QuarkusJpaProjectionPosition();

        assertThat(position.getStreamId()).isNull();
        assertThat(position.getNextEventNumber()).isEqualTo(0L);
    }
}
