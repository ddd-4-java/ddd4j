package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    /** \u7528\u4e8e\u9694\u79bb <code>Repository&lt;TestAggregate, String&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Repository<TestAggregate, String> repository;

    /** \u6d4b\u8bd5\u4e2d\u5b9e\u9645\u6784\u9020\u6216\u4f20\u9012\u7684\u67e5\u8be2\u6761\u4ef6\u3002 */
    private TestQuery query;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>query = new TestQuery(repository)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        query = new TestQuery(repository);
    }

    // =================== list() ===================

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void list_shouldDelegateToRepository() {
        List<TestAggregate> expected = List.of(new TestAggregate("1", "A"));
        when(repository.findList(any())).thenReturn(expected);

        List<TestAggregate> result = query.list();

        assertThat(result).isEqualTo(expected);
        verify(repository).findList(query);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void list_withEmptyResult_shouldReturnEmptyList() {
        when(repository.findList(any())).thenReturn(Collections.emptyList());

        List<TestAggregate> result = query.list();

        assertThat(result).isEmpty();
    }

    /** \u9a8c\u8bc1<code>query.list("No orders found")</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void list_withIfEmpty_shouldThrowWhenEmpty() {
        when(repository.findList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> query.list("No orders found"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("No orders found");
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void list_withIfEmpty_shouldReturnWhenNotEmpty() {
        List<TestAggregate> expected = List.of(new TestAggregate("1", "A"));
        when(repository.findList(any())).thenReturn(expected);

        List<TestAggregate> result = query.list("No orders found");

        assertThat(result).isEqualTo(expected);
    }

    // =================== page() ===================

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void page_shouldDelegateToRepository() {
        Page<TestAggregate> expected = Page.succeed(List.of(new TestAggregate("1", "A")), 1, 1, 10);
        when(repository.page(any())).thenReturn(expected);

        Page<TestAggregate> result = query.current(1).size(10).page();

        assertThat(result).isEqualTo(expected);
        verify(repository).page(query);
    }

    /** \u9a8c\u8bc1<code>result.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void page_withEmptyResult_shouldReturnEmptyPage() {
        Page<TestAggregate> emptyPage = Page.empty();
        when(repository.page(any())).thenReturn(emptyPage);

        Page<TestAggregate> result = query.page();

        assertThat(result.isEmpty()).isTrue();
    }

    /** \u9a8c\u8bc1<code>query.page("No data")</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void page_withIfEmpty_shouldThrowWhenEmpty() {
        Page<TestAggregate> emptyPage = Page.empty();
        when(repository.page(any())).thenReturn(emptyPage);

        assertThatThrownBy(() -> query.page("No data"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("No data");
    }

    // =================== one() / oneOpt() ===================

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void one_shouldDelegateToRepository() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        TestAggregate result = query.one();

        assertThat(result).isEqualTo(expected);
        verify(repository).findFirst(query);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void one_withNoResult_shouldReturnNull() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        TestAggregate result = query.one();

        assertThat(result).isNull();
    }

    /** \u9a8c\u8bc1<code>query.one("Order not found")</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void one_withIfNull_shouldThrowWhenNull() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.one("Order not found"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order not found");
    }

    /** \u9a8c\u8bc1<code>result</code>\u5b58\u5728\u503c\u3002 */
    @Test
    void oneOpt_shouldReturnOptional() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        Optional<TestAggregate> result = query.oneOpt();

        assertThat(result).isPresent().contains(expected);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void oneOpt_withNoResult_shouldReturnEmptyOptional() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        Optional<TestAggregate> result = query.oneOpt();

        assertThat(result).isEmpty();
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void first_shouldBehaveLikeOne() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        TestAggregate result = query.first();

        assertThat(result).isEqualTo(expected);
    }

    /** \u9a8c\u8bc1<code>result</code>\u5b58\u5728\u503c\u3002 */
    @Test
    void firstOpt_shouldBehaveLikeOneOpt() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        Optional<TestAggregate> result = query.firstOpt();

        assertThat(result).isPresent().contains(expected);
    }

    // =================== count() ===================

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>42L</code>\u3002 */
    @Test
    void count_shouldDelegateToRepository() {
        when(repository.count(any())).thenReturn(42L);

        long result = query.count();

        assertThat(result).isEqualTo(42L);
        verify(repository).count(query);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u96f6\u3002 */
    @Test
    void count_withZeroResult_shouldReturnZero() {
        when(repository.count(any())).thenReturn(0L);

        long result = query.count();

        assertThat(result).isZero();
    }

    // =================== exists() / exist() / notExist() ===================

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\u3002 */
    @Test
    void exists_shouldReturnTrueWhenCountGreaterThanZero() {
        when(repository.count(any())).thenReturn(5L);

        boolean result = query.exists();

        assertThat(result).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u5047\u3002 */
    @Test
    void exists_shouldReturnFalseWhenCountIsZero() {
        when(repository.count(any())).thenReturn(0L);

        boolean result = query.exists();

        assertThat(result).isFalse();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\u3002 */
    @Test
    void exist_shouldBehaveLikeExists() {
        when(repository.count(any())).thenReturn(1L);

        boolean result = query.exist();

        assertThat(result).isTrue();
    }

    /** \u9a8c\u8bc1<code>query.exist("Order does not exist")</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void exist_withIfNotExist_shouldThrowWhenNotExist() {
        when(repository.count(any())).thenReturn(0L);

        assertThatThrownBy(() -> query.exist("Order does not exist"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order does not exist");
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\u3002 */
    @Test
    void notExist_shouldReturnTrueWhenCountIsZero() {
        when(repository.count(any())).thenReturn(0L);

        boolean result = query.notExist();

        assertThat(result).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u5047\u3002 */
    @Test
    void notExist_shouldReturnFalseWhenCountGreaterThanZero() {
        when(repository.count(any())).thenReturn(1L);

        boolean result = query.notExist();

        assertThat(result).isFalse();
    }

    /** \u9a8c\u8bc1<code>query.notExist("Order already exists")</code> \u629b\u51fa <code>BizRuntimeException.class</code>\u3002 */
    @Test
    void notExist_withIfExist_shouldThrowWhenExist() {
        when(repository.count(any())).thenReturn(1L);

        assertThatThrownBy(() -> query.notExist("Order already exists"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order already exists");
    }

    // =================== maps() / map() ===================

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void maps_shouldDelegateToRepository() {
        List<Map<String, Object>> expected = List.of(Map.of("id", "1", "name", "A"));
        when(repository.maps(any())).thenReturn(expected);

        List<Map<String, Object>> result = query.maps();

        assertThat(result).isEqualTo(expected);
        verify(repository).maps(query);
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void map_shouldReturnFirstElement() {
        Map<String, Object> expected = Map.of("id", "1", "name", "A");
        when(repository.maps(any())).thenReturn(List.of(expected));

        Map<String, Object> result = query.map();

        assertThat(result).isEqualTo(expected);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void map_withEmptyResult_shouldReturnEmptyMap() {
        when(repository.maps(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = query.map();

        assertThat(result).isEmpty();
    }

    // =================== 条件构建链式调用 ===================

    /** \u9a8c\u8bc1<code>query.getWhereConditions()</code> \u7684\u5927\u5c0f\u4e3a <code>3</code>\uff0c<code>query.getOrderByConditions()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>query.getSize()</code> \u7b49\u4e8e <code>20</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>query.getWhereConditions()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>query.getWhereConditions().get(0).value()</code> \u7b49\u4e8e <code>"should be included"</code>\u3002 */
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
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;
        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;
        /** \u53d1\u9001\u7ed9\u5f53\u524d\u5939\u5177\u6216\u7528\u4e8e\u65ad\u8a00\u5f80\u8fd4\u7ed3\u679c\u7684\u503c\u3002 */
        private int value;
        /** \u7528\u4e8e\u9a8c\u8bc1\u5c55\u793a\u5b57\u6bb5\u3001\u6d88\u606f\u6216\u6ce8\u89e3\u5143\u6570\u636e\u6620\u5c04\u7684\u8bf4\u660e\u6587\u672c\u3002 */
        private String description;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        private Date createdAt;

        /**
         * \u8bbe\u7f6e <code>id</code>\u3001<code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregate</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        TestAggregate(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
         */
        String getName() {
            return name;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        int getValue() {
            return value;
        }

        /**
         * \u8bfb\u53d6<code>description</code> \u5b57\u6bb5\u3002
         * @return <code>description</code> \u5b57\u6bb5
         */
        String getDescription() {
            return description;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4
         */
        Date getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 测试用查询类（直接绑定 Repository）。
     */
    static final class TestQuery extends Query<TestAggregate> {
        /** \u7528\u4e8e\u6d4b\u8bd5\u67e5\u8be2\u6216\u4e8b\u52a1\u6548\u679c\u7684\u4ed3\u50a8\u5b9e\u4f8b\u3002 */
        private final Repository<TestAggregate, String> repo;

        /**
         * \u8bbe\u7f6e <code>repo</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestQuery</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param repo \u7528\u4e8e\u521d\u59cb\u5316 <code>TestQuery</code> \u7684 <code>Repository&lt;TestAggregate, String&gt;</code> \u503c
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
