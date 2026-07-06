package io.ddd4j.kit.lang;

import lombok.Data;
import org.junit.jupiter.api.Test;

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

    @Test
    void copy_toTargetClass_shouldReturnNullForNullSource() {
        assertThat(BeanKit.copy(null, TargetBean.class)).isNull();
    }

    @Test
    void copy_collectionToList_shouldCopyEachElement() {
        SourceBean s1 = new SourceBean();
        s1.setName("a");
        SourceBean s2 = new SourceBean();
        s2.setName("b");

        List<TargetBean> result = BeanKit.copy(List.of(s1, s2), TargetBean.class);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TargetBean::getName).containsExactly("a", "b");
    }

    @Test
    void toMap_shouldConvertBeanProperties() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        source.setAge(30);

        Map<String, Object> map = BeanKit.toMap(source);

        assertThat(map).containsEntry("name", "alice");
        assertThat(map).containsEntry("age", 30);
    }

    @Test
    void toMap_shouldReturnNullForNullSource() {
        assertThat(BeanKit.toMap(null)).isNull();
    }

    @Test
    void toMapClean_shouldExcludeNullValues() {
        SourceBean source = new SourceBean();
        source.setName("alice");
        // age left null

        Map<String, Object> map = BeanKit.toMapClean(source);

        assertThat(map).containsEntry("name", "alice");
        assertThat(map).doesNotContainKey("age");
    }

    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(BeanKit.isEmpty(null)).isTrue();
    }

    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(BeanKit.isEmpty("")).isTrue();
    }

    @Test
    void isEmpty_shouldReturnTrueForEmptyCollection() {
        assertThat(BeanKit.isEmpty(java.util.Collections.emptyList())).isTrue();
    }

    @Test
    void isEmpty_shouldReturnFalseForNonEmptyString() {
        assertThat(BeanKit.isEmpty("x")).isFalse();
    }

    @Test
    void changeColumnToFieldName_shouldConvertSnakeCaseToCamelCase() {
        assertThat(BeanKit.changeColumnToFieldName("user_name")).isEqualTo("userName");
        assertThat(BeanKit.changeColumnToFieldName("create_time")).isEqualTo("createTime");
        assertThat(BeanKit.changeColumnToFieldName("id")).isEqualTo("id");
    }

    @Test
    void changeColumnToFieldName_shouldReturnSameForNull() {
        assertThat(BeanKit.changeColumnToFieldName(null)).isNull();
    }

    @Test
    void listToString_shouldJoinWithCommaByDefault() {
        assertThat(BeanKit.listToString(List.of("a", "b", "c"))).isEqualTo("a,b,c");
    }

    @Test
    void listToString_shouldJoinWithCustomSeparator() {
        assertThat(BeanKit.listToString(List.of("a", "b"), ";")).isEqualTo("a;b");
    }

    @Test
    void listToString_shouldWrapWithSurround() {
        assertThat(BeanKit.listToString(List.of("a", "b"), ",", "'")).isEqualTo("'a','b'");
    }

    @Test
    void listToString_shouldReturnEmptyForEmptyList() {
        assertThat(BeanKit.listToString(java.util.Collections.emptyList())).isEmpty();
    }

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

    @Data
    static class SourceBean {
        private String name;
        private Integer age;
    }

    @Data
    static class TargetBean {
        private String name;
        private Integer age;
    }
}
