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
package io.ddd4j.kit.lang;

import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BeanKit} (bean copy / map / list helpers).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BeanKitTest {

    /** \u9a8c\u8bc1<code>target.getName()</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>target.getAge()</code> \u7b49\u4e8e <code>30</code>\u3002 */
    @Test
    void copy_toTarget_shouldCopyNonNullProperties() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        source.setAge(30);

        TargetBean target = new TargetBean();
        BeanKit.copy(source, target);

        assertThat(target.getName()).isEqualTo("alice");
        assertThat(target.getAge()).isEqualTo(30);
    }

    /** \u9a8c\u8bc1<code>target</code>\u975e\u7a7a\uff0c<code>target.getName()</code> \u7b49\u4e8e <code>"bob"</code>\uff0c<code>target.getAge()</code> \u7b49\u4e8e <code>25</code>\u3002 */
    @Test
    void copy_toTargetClass_shouldReturnNewInstance() {
        SourceBean source = new SourceBean();
        source.setName("bob");
        source.setAge(25);

        TargetBean target = BeanKit.copy(source, TargetBean.class);

        assertThat(target).isNotNull();
        assertThat(target.getName()).isEqualTo("bob");
        assertThat(target.getAge()).isEqualTo(25);
    }

    /** \u9a8c\u8bc1<code>BeanKit.copy(null, TargetBean.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void copy_toTargetClass_shouldReturnNullForNullSource() {
        assertThat(BeanKit.copy(null, TargetBean.class)).isNull();
    }

    /** \u9a8c\u8bc1<code>result</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\u3002 */
    @Test
    void copy_collectionToList_shouldCopyEachElement() {
        SourceBean s1 = new SourceBean();
        s1.setName("a");
        SourceBean s2 = new SourceBean();
        s2.setName("b");

        List<TargetBean> result = BeanKit.copy(Arrays.asList(s1, s2), TargetBean.class);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TargetBean::getName).containsExactly("a", "b");
    }

    /** \u9a8c\u8bc1<code>map</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"name"</code>\u3001<code>"alice"</code>\uff0c<code>map</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"age"</code>\u3001<code>30</code>\u3002 */
    @Test
    void toMap_shouldConvertBeanProperties() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        source.setAge(30);

        Map<String, Object> map = BeanKit.toMap(source);

        assertThat(map).containsEntry("name", "alice");
        assertThat(map).containsEntry("age", 30);
    }

    /** \u9a8c\u8bc1<code>BeanKit.toMap(null)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void toMap_shouldReturnNullForNullSource() {
        assertThat(BeanKit.toMap(null)).isNull();
    }

    /** \u9a8c\u8bc1<code>map</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"name"</code>\u3001<code>"alice"</code>\u3002 */
    @Test
    void toMapClean_shouldExcludeNullValues() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        // age left null

        Map<String, Object> map = BeanKit.toMapClean(source);

        assertThat(map).containsEntry("name", "alice");
        assertThat(map).doesNotContainKey("age");
    }

    /** \u9a8c\u8bc1<code>BeanKit.isEmpty(null)</code>\u4e3a\u771f\u3002 */
    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(BeanKit.isEmpty(null)).isTrue();
    }

    /** \u9a8c\u8bc1<code>BeanKit.isEmpty("")</code>\u4e3a\u771f\u3002 */
    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(BeanKit.isEmpty("")).isTrue();
    }

    /** \u9a8c\u8bc1<code>BeanKit.isEmpty(java.util.Collections.emptyList())</code>\u4e3a\u771f\u3002 */
    @Test
    void isEmpty_shouldReturnTrueForEmptyCollection() {
        assertThat(BeanKit.isEmpty(java.util.Collections.emptyList())).isTrue();
    }

    /** \u9a8c\u8bc1<code>BeanKit.isEmpty("x")</code>\u4e3a\u5047\u3002 */
    @Test
    void isEmpty_shouldReturnFalseForNonEmptyString() {
        assertThat(BeanKit.isEmpty("x")).isFalse();
    }

    /** \u9a8c\u8bc1<code>BeanKit.changeColumnToFieldName("user_name")</code> \u7b49\u4e8e <code>"userName"</code>\uff0c<code>BeanKit.changeColumnToFieldName("create_time")</code> \u7b49\u4e8e <code>"createTime"</code>\uff0c<code>BeanKit.changeColumnToFieldName("id")</code> \u7b49\u4e8e <code>"id"</code>\u3002 */
    @Test
    void changeColumnToFieldName_shouldConvertSnakeCaseToCamelCase() {
        assertThat(BeanKit.changeColumnToFieldName("user_name")).isEqualTo("userName");
        assertThat(BeanKit.changeColumnToFieldName("create_time")).isEqualTo("createTime");
        assertThat(BeanKit.changeColumnToFieldName("id")).isEqualTo("id");
    }

    /** \u9a8c\u8bc1<code>BeanKit.changeColumnToFieldName(null)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void changeColumnToFieldName_shouldReturnSameForNull() {
        assertThat(BeanKit.changeColumnToFieldName(null)).isNull();
    }

    /** \u9a8c\u8bc1\u672a\u63d0\u4f9b\u5206\u9694\u7b26\u65f6\u4f7f\u7528\u9017\u53f7\u8fde\u63a5\u5217\u8868\u5143\u7d20\u3002 */
    @Test
    void listToString_shouldJoinWithCommaByDefault() {
        assertThat(BeanKit.listToString(Arrays.asList("a", "b", "c"))).isEqualTo("a,b,c");
    }

    /** \u9a8c\u8bc1\u5217\u8868\u4e2d\u7684\u5143\u7d20\u4f7f\u7528\u8c03\u7528\u65b9\u7ed9\u51fa\u7684\u5206\u9694\u7b26\u8fde\u63a5\u3002 */
    @Test
    void listToString_shouldJoinWithCustomSeparator() {
        assertThat(BeanKit.listToString(Arrays.asList("a", "b"), ";")).isEqualTo("a;b");
    }

    /** \u9a8c\u8bc1\u8fde\u63a5\u524d\u4e3a\u6bcf\u4e2a\u5143\u7d20\u6dfb\u52a0\u6307\u5b9a\u7684\u524d\u540e\u5305\u88f9\u5b57\u7b26\u3002 */
    @Test
    void listToString_shouldWrapWithSurround() {
        assertThat(BeanKit.listToString(Arrays.asList("a", "b"), ",", "'")).isEqualTo("'a','b'");
    }

    /** \u9a8c\u8bc1<code>BeanKit.listToString(java.util.Collections.emptyList())</code>\u4e3a\u7a7a\u3002 */
    @Test
    void listToString_shouldReturnEmptyForEmptyList() {
        assertThat(BeanKit.listToString(java.util.Collections.emptyList())).isEmpty();
    }

    /** \u9a8c\u8bc1<code>bean</code>\u975e\u7a7a\uff0c<code>bean.getName()</code> \u7b49\u4e8e <code>"carol"</code>\uff0c<code>bean.getAge()</code> \u7b49\u4e8e <code>40</code>\u3002 */
    @Test
    void ofMap_shouldConvertMapToBean() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "carol");
        map.put("age", 40);

        TargetBean bean = BeanKit.ofMap(map, TargetBean.class);

        assertThat(bean).isNotNull();
        assertThat(bean.getName()).isEqualTo("carol");
        assertThat(bean.getAge()).isEqualTo(40);
    }

    // ========================= Fixtures =========================

    /**
     * 拷贝测试的源数据对象。
     */
    @Data
    static class SourceBean {
        /** \u88ab\u590d\u5236\u7684\u6e90 Bean \u59d3\u540d\u5c5e\u6027\u3002 */
        private String name;
        /** \u88ab\u590d\u5236\u7684\u6e90 Bean \u5e74\u9f84\u5c5e\u6027\u3002 */
        private Integer age;
    }

    /**
     * 拷贝测试的目标数据对象。
     */
    @Data
    static class TargetBean {
        /** \u63a5\u6536\u590d\u5236\u7ed3\u679c\u7684 Bean \u59d3\u540d\u5c5e\u6027\u3002 */
        private String name;
        /** \u63a5\u6536\u590d\u5236\u7ed3\u679c\u7684 Bean \u5e74\u9f84\u5c5e\u6027\u3002 */
        private Integer age;
    }
}
