package io.ddd4j.core.cqrs.query;

import java.util.Collections;
import java.util.Arrays;
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

    @Mock
    private Repository<TestAggregate, String> repository;

    private TestQuery query;

    @BeforeEach
    void setUp() {
        query = new TestQuery(repository);
    }

    // =================== list() ===================

    @Test
    void list_shouldDelegateToRepository() {
        List<TestAggregate> expected = Arrays.asList(new TestAggregate("1", "A"));
        when(repository.findList(any())).thenReturn(expected);

        List<TestAggregate> result = query.list();

        assertThat(result).isEqualTo(expected);
        verify(repository).findList(query);
    }

    @Test
    void list_withEmptyResult_shouldReturnEmptyList() {
        when(repository.findList(any())).thenReturn(Collections.emptyList());

        List<TestAggregate> result = query.list();

        assertThat(result).isEmpty();
    }

    @Test
    void list_withIfEmpty_shouldThrowWhenEmpty() {
        when(repository.findList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> query.list("No orders found"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("No orders found");
    }

    @Test
    void list_withIfEmpty_shouldReturnWhenNotEmpty() {
        List<TestAggregate> expected = Arrays.asList(new TestAggregate("1", "A"));
        when(repository.findList(any())).thenReturn(expected);

        List<TestAggregate> result = query.list("No orders found");

        assertThat(result).isEqualTo(expected);
    }

    // =================== page() ===================

    @Test
    void page_shouldDelegateToRepository() {
        Page<TestAggregate> expected = Page.succeed(Arrays.asList(new TestAggregate("1", "A")), 1, 1, 10);
        when(repository.page(any())).thenReturn(expected);

        Page<TestAggregate> result = query.current(1).size(10).page();

        assertThat(result).isEqualTo(expected);
        verify(repository).page(query);
    }

    @Test
    void page_withEmptyResult_shouldReturnEmptyPage() {
        Page<TestAggregate> emptyPage = Page.empty();
        when(repository.page(any())).thenReturn(emptyPage);

        Page<TestAggregate> result = query.page();

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void page_withIfEmpty_shouldThrowWhenEmpty() {
        Page<TestAggregate> emptyPage = Page.empty();
        when(repository.page(any())).thenReturn(emptyPage);

        assertThatThrownBy(() -> query.page("No data"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("No data");
    }

    // =================== one() / oneOpt() ===================

    @Test
    void one_shouldDelegateToRepository() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        TestAggregate result = query.one();

        assertThat(result).isEqualTo(expected);
        verify(repository).findFirst(query);
    }

    @Test
    void one_withNoResult_shouldReturnNull() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        TestAggregate result = query.one();

        assertThat(result).isNull();
    }

    @Test
    void one_withIfNull_shouldThrowWhenNull() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.one("Order not found"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void oneOpt_shouldReturnOptional() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        Optional<TestAggregate> result = query.oneOpt();

        assertThat(result).isPresent().contains(expected);
    }

    @Test
    void oneOpt_withNoResult_shouldReturnEmptyOptional() {
        when(repository.findFirst(any())).thenReturn(Optional.empty());

        Optional<TestAggregate> result = query.oneOpt();

        assertThat(result).isEmpty();
    }

    @Test
    void first_shouldBehaveLikeOne() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        TestAggregate result = query.first();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void firstOpt_shouldBehaveLikeOneOpt() {
        TestAggregate expected = new TestAggregate("1", "A");
        when(repository.findFirst(any())).thenReturn(Optional.of(expected));

        Optional<TestAggregate> result = query.firstOpt();

        assertThat(result).isPresent().contains(expected);
    }

    // =================== count() ===================

    @Test
    void count_shouldDelegateToRepository() {
        when(repository.count(any())).thenReturn(42L);

        long result = query.count();

        assertThat(result).isEqualTo(42L);
        verify(repository).count(query);
    }

    @Test
    void count_withZeroResult_shouldReturnZero() {
        when(repository.count(any())).thenReturn(0L);

        long result = query.count();

        assertThat(result).isZero();
    }

    // =================== exists() / exist() / notExist() ===================

    @Test
    void exists_shouldReturnTrueWhenCountGreaterThanZero() {
        when(repository.count(any())).thenReturn(5L);

        boolean result = query.exists();

        assertThat(result).isTrue();
    }

    @Test
    void exists_shouldReturnFalseWhenCountIsZero() {
        when(repository.count(any())).thenReturn(0L);

        boolean result = query.exists();

        assertThat(result).isFalse();
    }

    @Test
    void exist_shouldBehaveLikeExists() {
        when(repository.count(any())).thenReturn(1L);

        boolean result = query.exist();

        assertThat(result).isTrue();
    }

    @Test
    void exist_withIfNotExist_shouldThrowWhenNotExist() {
        when(repository.count(any())).thenReturn(0L);

        assertThatThrownBy(() -> query.exist("Order does not exist"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order does not exist");
    }

    @Test
    void notExist_shouldReturnTrueWhenCountIsZero() {
        when(repository.count(any())).thenReturn(0L);

        boolean result = query.notExist();

        assertThat(result).isTrue();
    }

    @Test
    void notExist_shouldReturnFalseWhenCountGreaterThanZero() {
        when(repository.count(any())).thenReturn(1L);

        boolean result = query.notExist();

        assertThat(result).isFalse();
    }

    @Test
    void notExist_withIfExist_shouldThrowWhenExist() {
        when(repository.count(any())).thenReturn(1L);

        assertThatThrownBy(() -> query.notExist("Order already exists"))
                .isInstanceOf(BizRuntimeException.class)
                .hasMessageContaining("Order already exists");
    }

    // =================== maps() / map() ===================

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

    @Test
    void map_shouldReturnFirstElement() {
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("id", "1");
        expected.put("name", "A");
        when(repository.maps(any())).thenReturn(Collections.singletonList(expected));

        Map<String, Object> result = query.map();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void map_withEmptyResult_shouldReturnEmptyMap() {
        when(repository.maps(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = query.map();

        assertThat(result).isEmpty();
    }

    // =================== 条件构建链式调用 ===================

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
        private final String id;
        private final String name;
        private int value;
        private String description;
        private Date createdAt;

        TestAggregate(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String id() {
            return id;
        }

        String getName() {
            return name;
        }

        int getValue() {
            return value;
        }

        String getDescription() {
            return description;
        }

        Date getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 测试用查询类（直接绑定 Repository）。
     */
    static final class TestQuery extends Query<TestAggregate> {
        private final Repository<TestAggregate, String> repo;

        TestQuery(Repository<TestAggregate, String> repo) {
            this.repo = repo;
        }

        @Override
        public Repository<TestAggregate, String> repository() {
            return repo;
        }
    }
}
