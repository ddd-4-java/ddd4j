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
package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Query} 充血查询方法（list/page/one/count/exists/maps）单元测试。
 *
 * <p>通过 Mock Repository 验证：
 * <ul>
 *   <li>list() - 列表查询</li>
 *   <li>page() - 分页查询</li>
 *   <li>one() / oneOpt() - 单条查询</li>
 *   <li>count() - 计数</li>
 *   <li>exists() / exist() / notExist() - 存在判断</li>
 *   <li>maps() / map() - Map 查询</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(MockitoExtension.class)
class QueryRichMethodsTest {

    /**
     * 用于隔离 <code>Repository&lt;TestAggregate, String&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Repository<TestAggregate, String> repository;

    /**
     * 测试中实际构造或传递的查询条件。
     */
    private TestQuery query;

    /**
     * 根据本次调用更新 <code>query = new TestQuery(repository)</code>，用于后续测试步骤。
     */
    @BeforeEach
    void setUp() {
        query = new TestQuery(repository);
    }

    // =================== list() ===================

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void list_shouldDelegateToRepository() {
        List<TestAggregate> expected = Arrays.asList(new TestAggregate("1", "A"));
        when(repository.findList(any())).thenReturn(expected);

        List<TestAggregate> result = query.list();

        assertThat(result).isEqualTo(expected);
        verify(repository).findList(query);
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void list_withEmptyResult_shouldReturnEmptyList() {
        when(repository.findList(any())).thenReturn(Collections.emptyList());

        List<TestAggregate> result = query.list();

        assertThat(result).isEmpty();
    }

    /**
     * 验证<code>query.list("No orders found")</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void list_withIfEmpty_shouldThrowWhenEmpty() {
        when(repository.findList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> query.list("No orders found"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("No orders found");
    }

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void list_withIfEmpty_shouldReturnWhenNotEmpty() {
        List<TestAggregate> expected = Arrays.asList(new TestAggregate("1", "A"));
        when(repository.findList(any())).thenReturn(expected);

        List<TestAggregate> result = query.list("No orders found");

        assertThat(result).isEqualTo(expected);
    }

    // =================== page() ===================

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void page_shouldDelegateToRepository() {
        Page<TestAggregate> expected = Page.succeed(Arrays.asList(new TestAggregate("1", "A")), 1, 1, 10);
        when(repository.page(any())).thenReturn(expected);

        Page<TestAggregate> result = query.current(1).size(10).page();

        assertThat(result).isEqualTo(expected);
        verify(repository).page(query);
    }

    /**
     * 验证<code>result.isEmpty()</code>为真。
     */
    @Test
    void page_withEmptyResult_shouldReturnEmptyPage() {
        Page<TestAggregate> emptyPage = Page.empty();
        when(repository.page(any())).thenReturn(emptyPage);

        Page<TestAggregate> result = query.page();

        assertThat(result.isEmpty()).isTrue();
    }

    /**
     * 验证<code>query.page("No data")</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void page_withIfEmpty_shouldThrowWhenEmpty() {
        Page<TestAggregate> emptyPage = Page.empty();
        when(repository.page(any())).thenReturn(emptyPage);

        assertThatThrownBy(() -> query.page("No data"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("No data");
    }

    // =================== one() / oneOpt() ===================

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void one_shouldDelegateToRepository() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        TestAggregate result = query.one();

        assertThat(result).isEqualTo(expected);
        verify(repository).findFirst(query);
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void one_withNoResult_shouldReturnNull() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        TestAggregate result = query.one();

        assertThat(result).isNull();
    }

    /**
     * 验证<code>query.one("Order not found")</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void one_withIfNull_shouldThrowWhenNull() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.one("Order not found"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order not found");
    }

    /**
     * 验证<code>result</code>存在值。
     */
    @Test
    void oneOpt_shouldReturnOptional() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        Optional<TestAggregate> result = query.oneOpt();

        assertThat(result).isPresent().contains(expected);
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void oneOpt_withNoResult_shouldReturnEmptyOptional() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        Optional<TestAggregate> result = query.oneOpt();

        assertThat(result).isEmpty();
    }

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void first_shouldBehaveLikeOne() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        TestAggregate result = query.first();

        assertThat(result).isEqualTo(expected);
    }

    /**
     * 验证<code>result</code>存在值。
     */
    @Test
    void firstOpt_shouldBehaveLikeOneOpt() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        Optional<TestAggregate> result = query.firstOpt();

        assertThat(result).isPresent().contains(expected);
    }

    // =================== count() ===================

    /**
     * 验证<code>result</code> 等于 <code>42L</code>。
     */
    @Test
    void count_shouldDelegateToRepository() {
        when(repository.count(any())).thenReturn(42L);

        long result = query.count();

        assertThat(result).isEqualTo(42L);
        verify(repository).count(query);
    }

    /**
     * 验证<code>result</code>为零。
     */
    @Test
    void count_withZeroResult_shouldReturnZero() {
        when(repository.count(any())).thenReturn(0L);

        long result = query.count();

        assertThat(result).isZero();
    }

    // =================== exists() / exist() / notExist() ===================

    /**
     * 验证<code>result</code>为真。
     */
    @Test
    void exists_shouldReturnTrueWhenCountGreaterThanZero() {
        when(repository.count(any())).thenReturn(5L);

        boolean result = query.exists();

        assertThat(result).isTrue();
    }

    /**
     * 验证<code>result</code>为假。
     */
    @Test
    void exists_shouldReturnFalseWhenCountIsZero() {
        when(repository.count(any())).thenReturn(0L);

        boolean result = query.exists();

        assertThat(result).isFalse();
    }

    /**
     * 验证<code>result</code>为真。
     */
    @Test
    void exist_shouldBehaveLikeExists() {
        when(repository.count(any())).thenReturn(1L);

        boolean result = query.exist();

        assertThat(result).isTrue();
    }

    /**
     * 验证<code>query.exist("Order does not exist")</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void exist_withIfNotExist_shouldThrowWhenNotExist() {
        when(repository.count(any())).thenReturn(0L);

        assertThatThrownBy(() -> query.exist("Order does not exist"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order does not exist");
    }

    /**
     * 验证<code>result</code>为真。
     */
    @Test
    void notExist_shouldReturnTrueWhenCountIsZero() {
        when(repository.count(any())).thenReturn(0L);

        boolean result = query.notExist();

        assertThat(result).isTrue();
    }

    /**
     * 验证<code>result</code>为假。
     */
    @Test
    void notExist_shouldReturnFalseWhenCountGreaterThanZero() {
        when(repository.count(any())).thenReturn(1L);

        boolean result = query.notExist();

        assertThat(result).isFalse();
    }

    /**
     * 验证<code>query.notExist("Order already exists")</code> 抛出 <code>BizRuntimeException.class</code>。
     */
    @Test
    void notExist_withIfExist_shouldThrowWhenExist() {
        when(repository.count(any())).thenReturn(1L);

        assertThatThrownBy(() -> query.notExist("Order already exists"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order already exists");
    }

    // =================== maps() / map() ===================

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void maps_shouldDelegateToRepository() {
        Map<String, Object> expectedMap = new java.util.LinkedHashMap<>();
        expectedMap.put("id", "1");
        expectedMap.put("name", "A");
        List<Map<String, Object>> expected = Collections.singletonList(expectedMap);
        when(repository.maps(any())).thenReturn(expected);

        List<Map<String, Object>> result = query.maps();

        assertThat(result).isEqualTo(expected);
        verify(repository).maps(query);
    }

    /**
     * 验证<code>result</code> 等于 <code>expected</code>。
     */
    @Test
    void map_shouldReturnFirstElement() {
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("id", "1");
        expected.put("name", "A");
        when(repository.maps(any())).thenReturn(Collections.singletonList(expected));

        Map<String, Object> result = query.map();

        assertThat(result).isEqualTo(expected);
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void map_withEmptyResult_shouldReturnEmptyMap() {
        when(repository.maps(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = query.map();

        assertThat(result).isEmpty();
    }

    // =================== 条件构建链式调用 ===================

    /**
     * 验证<code>query.getWhereConditions()</code> 的大小为 <code>3</code>，<code>query.getOrderByConditions()</code> 的大小为 <code>1</code>，<code>query.getSize()</code> 等于 <code>20</code>。
     */
    @Test
    void chainedConditions_shouldBuildCorrectly() {
        query.eq(TestAggregate::getName, "test")
                .gt(TestAggregate::getValue, 10)
                .like(TestAggregate::getDescription, "%keyword%")
                .orderByDesc(TestAggregate::getCreatedAt)
                .current(2)
                .size(20);

        assertThat(query.getWhereConditions()).hasSize(3);
        assertThat(query.getOrderByConditions()).hasSize(1);
        assertThat(query.getCurrent()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(20);
    }

    /**
     * 验证<code>query.getWhereConditions()</code> 的大小为 <code>1</code>，<code>query.getWhereConditions().get(0).value()</code> 等于 <code>"should be included"</code>。
     */
    @Test
    void conditionalMethods_shouldRespectBooleanFlag() {
        query.eq(false, TestAggregate::getName, "should be ignored")
                .eq(true, TestAggregate::getName, "should be included");

        assertThat(query.getWhereConditions()).hasSize(1);
        assertThat(query.getWhereConditions().get(0).value()).isEqualTo("should be included");
    }

    // =================== 辅助类 ===================

    /**
     * 测试用聚合根。
     */
    static final class TestAggregate extends AggregateRoot<String> {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id;
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;
        /**
         * 发送给当前夹具或用于断言往返结果的值。
         */
        private int value;
        /**
         * 用于验证展示字段、消息或注解元数据映射的说明文本。
         */
        private String description;
        /**
         * 记录创建的时间。
         */
        private Date createdAt;

        /**
         * 设置 <code>id</code>、<code>name</code>，用于构造 <code>TestAggregate</code> 测试实例。
         *
         * @param id   标识当前测试对象的主键或事件标识
         * @param name 当前测试指定的对象或属性名称
         */
        TestAggregate(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 读取<code>name</code> 字段。
         *
         * @return <code>name</code> 字段
         */
        String getName() {
            return name;
        }

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
         */
        int getValue() {
            return value;
        }

        /**
         * 读取<code>description</code> 字段。
         *
         * @return <code>description</code> 字段
         */
        String getDescription() {
            return description;
        }

        /**
         * 读取记录创建的时间。
         *
         * @return 记录创建的时间
         */
        Date getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 测试用查询类（直接绑定 Repository）。
     */
    static final class TestQuery extends Query<TestAggregate> {
        /**
         * 用于测试查询或事务效果的仓储实例。
         */
        private final Repository<TestAggregate, String> repo;

        /**
         * 设置 <code>repo</code>，用于构造 <code>TestQuery</code> 测试实例。
         *
         * @param repo 用于初始化 <code>TestQuery</code> 的 <code>Repository&lt;TestAggregate, String&gt;</code> 值
         */
        TestQuery(Repository<TestAggregate, String> repo) {
            this.repo = repo;
        }

        /**
         * 返回当前用例依赖的仓储。
         *
         * @return 实际返回<code>repo</code>，供调用方取得当前值
         */
        @Override
        public Repository<TestAggregate, String> repository() {
            return repo;
        }
    }
}
