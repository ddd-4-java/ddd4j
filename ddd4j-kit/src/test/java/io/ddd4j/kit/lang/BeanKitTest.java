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

    /**
     * 验证<code>target.getName()</code> 等于 <code>"alice"</code>，<code>target.getAge()</code> 等于 <code>30</code>。
     */
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

    /**
     * 验证<code>target</code>非空，<code>target.getName()</code> 等于 <code>"bob"</code>，<code>target.getAge()</code> 等于 <code>25</code>。
     */
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

    /**
     * 验证<code>BeanKit.copy(null, TargetBean.class)</code>为空。
     */
    @Test
    void copy_toTargetClass_shouldReturnNullForNullSource() {
        assertThat(BeanKit.copy(null, TargetBean.class)).isNull();
    }

    /**
     * 验证<code>result</code> 的大小为 <code>2</code>。
     */
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

    /**
     * 验证<code>map</code> 含对应条目 <code>"name"</code>、<code>"alice"</code>，<code>map</code> 含对应条目 <code>"age"</code>、<code>30</code>。
     */
    @Test
    void toMap_shouldConvertBeanProperties() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        source.setAge(30);

        Map<String, Object> map = BeanKit.toMap(source);

        assertThat(map).containsEntry("name", "alice");
        assertThat(map).containsEntry("age", 30);
    }

    /**
     * 验证<code>BeanKit.toMap(null)</code>为空。
     */
    @Test
    void toMap_shouldReturnNullForNullSource() {
        assertThat(BeanKit.toMap(null)).isNull();
    }

    /**
     * 验证<code>map</code> 含对应条目 <code>"name"</code>、<code>"alice"</code>。
     */
    @Test
    void toMapClean_shouldExcludeNullValues() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        // age left null

        Map<String, Object> map = BeanKit.toMapClean(source);

        assertThat(map).containsEntry("name", "alice");
        assertThat(map).doesNotContainKey("age");
    }

    /**
     * 验证<code>BeanKit.isEmpty(null)</code>为真。
     */
    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(BeanKit.isEmpty(null)).isTrue();
    }

    /**
     * 验证<code>BeanKit.isEmpty("")</code>为真。
     */
    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(BeanKit.isEmpty("")).isTrue();
    }

    /**
     * 验证<code>BeanKit.isEmpty(java.util.Collections.emptyList())</code>为真。
     */
    @Test
    void isEmpty_shouldReturnTrueForEmptyCollection() {
        assertThat(BeanKit.isEmpty(java.util.Collections.emptyList())).isTrue();
    }

    /**
     * 验证<code>BeanKit.isEmpty("x")</code>为假。
     */
    @Test
    void isEmpty_shouldReturnFalseForNonEmptyString() {
        assertThat(BeanKit.isEmpty("x")).isFalse();
    }

    /**
     * 验证<code>BeanKit.changeColumnToFieldName("user_name")</code> 等于 <code>"userName"</code>，<code>BeanKit.changeColumnToFieldName("create_time")</code> 等于 <code>"createTime"</code>，<code>BeanKit.changeColumnToFieldName("id")</code> 等于 <code>"id"</code>。
     */
    @Test
    void changeColumnToFieldName_shouldConvertSnakeCaseToCamelCase() {
        assertThat(BeanKit.changeColumnToFieldName("user_name")).isEqualTo("userName");
        assertThat(BeanKit.changeColumnToFieldName("create_time")).isEqualTo("createTime");
        assertThat(BeanKit.changeColumnToFieldName("id")).isEqualTo("id");
    }

    /**
     * 验证<code>BeanKit.changeColumnToFieldName(null)</code>为空。
     */
    @Test
    void changeColumnToFieldName_shouldReturnSameForNull() {
        assertThat(BeanKit.changeColumnToFieldName(null)).isNull();
    }

    /**
     * 验证未提供分隔符时使用逗号连接列表元素。
     */
    @Test
    void listToString_shouldJoinWithCommaByDefault() {
        assertThat(BeanKit.listToString(Arrays.asList("a", "b", "c"))).isEqualTo("a,b,c");
    }

    /**
     * 验证列表中的元素使用调用方给出的分隔符连接。
     */
    @Test
    void listToString_shouldJoinWithCustomSeparator() {
        assertThat(BeanKit.listToString(Arrays.asList("a", "b"), ";")).isEqualTo("a;b");
    }

    /**
     * 验证连接前为每个元素添加指定的前后包裹字符。
     */
    @Test
    void listToString_shouldWrapWithSurround() {
        assertThat(BeanKit.listToString(Arrays.asList("a", "b"), ",", "'")).isEqualTo("'a','b'");
    }

    /**
     * 验证<code>BeanKit.listToString(java.util.Collections.emptyList())</code>为空。
     */
    @Test
    void listToString_shouldReturnEmptyForEmptyList() {
        assertThat(BeanKit.listToString(java.util.Collections.emptyList())).isEmpty();
    }

    /**
     * 验证<code>bean</code>非空，<code>bean.getName()</code> 等于 <code>"carol"</code>，<code>bean.getAge()</code> 等于 <code>40</code>。
     */
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
        /**
         * 被复制的源 Bean 姓名属性。
         */
        private String name;
        /**
         * 被复制的源 Bean 年龄属性。
         */
        private Integer age;
    }

    /**
     * 拷贝测试的目标数据对象。
     */
    @Data
    static class TargetBean {
        /**
         * 接收复制结果的 Bean 姓名属性。
         */
        private String name;
        /**
         * 接收复制结果的 Bean 年龄属性。
         */
        private Integer age;
    }
}
