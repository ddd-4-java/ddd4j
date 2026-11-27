package io.ddd4j.sample.quarkus.satoken.goods.web;

import io.ddd4j.core.api.Page;
import io.ddd4j.sample.quarkus.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.satoken.goods.domain.Goods;
import io.ddd4j.sample.quarkus.satoken.goods.infrastructure.InMemoryGoodsRepository;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 旧商品 Java 入口复用真实 CDI 查询资源，同时保持每个 GET 路径只有一个绑定。
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.satoken.GoodsStandaloneProfile.class)
class GoodsResourceCompatibilityContractTest {
    /** \u8bb0\u5f55\u793a\u4f8b\u6d4b\u8bd5\u6570\u636e\u662f\u5426\u5df2\u7ecf\u51c6\u5907\u5b8c\u6210\u3002 */
    private final List<Goods> seeded = new ArrayList<>();
    /** \u5904\u7406\u5546\u54c1\u521b\u5efa\u3001\u7ef4\u62a4\u548c\u67e5\u8be2\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private GoodsApplicationService applicationService;
    /** \u5c06\u5f53\u524d\u6d4b\u8bd5\u67e5\u8be2\u8f6c\u4ea4\u7ed9\u5e94\u7528\u670d\u52a1\u7684 HTTP \u8d44\u6e90\u3002 */
    private GoodsQueryResource queryResource;
    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    private InMemoryGoodsRepository repository;
    /** \u6d4b\u8bd5\u521b\u5efa\u8def\u7531\u6216\u4e1a\u52a1\u6807\u8bc6\u65f6\u4f7f\u7528\u7684\u524d\u7f00\u3002 */
    private String prefix;

    /**
     * \u6838\u5bf9<code>Arrays.stream()</code>\u3001<code>method.getParameters()</code>\u3001<code>parameter.getName()</code> \u8fd4\u56de\u7684\u6d4b\u8bd5\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     */
    private static void assertNames(Method method, String... expected) {
        assertArrayEquals(expected, Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getName()).toArray(String[]::new));
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>Arc.container()</code>\u3001<code>UUID.randomUUID()</code>\u3001<code>seeded.add()</code>\u3001<code>applicationService.create()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void seedActualRepository() {
        applicationService = Arc.container().instance(GoodsApplicationService.class).get();
        queryResource = Arc.container().instance(GoodsQueryResource.class).get();
        repository = Arc.container().instance(InMemoryGoodsRepository.class).get();
        prefix = "query-compat-" + UUID.randomUUID();
        seeded.add(applicationService.create(prefix + "-low", prefix + " item", new BigDecimal("10"), 1));
        seeded.add(applicationService.create(prefix + "-middle", prefix + " item", new BigDecimal("20"), 1));
        seeded.add(applicationService.create(prefix + "-high", prefix + " item", new BigDecimal("30"), 1));
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>repository.deleteById()</code>\u3001<code>goods.getId()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void removeOnlyOurRows() {
        if (Objects.nonNull(repository)) {
            for (Goods goods : seeded) {
                repository.deleteById(goods.getId());
            }
        }
    }

    /** \u9a8c\u8bc1<code>page.getTotal()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>page.getCurrent()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>page.getRecords().get(0).getCode()</code> \u7b49\u4e8e <code>prefix + "-middle"</code>\u3002 */
    @Test
    void oldJavaConstructorsAndQueryMethodsUseRealFiltersAndPagination() {
        GoodsResource resource = new GoodsResource(applicationService, queryResource);
        Page<Goods> page = resource.page(2L, 1L, null, prefix, "draft",
                new BigDecimal("15"), new BigDecimal("30"), "price_DESC").getData();
        assertEquals(2L, page.getTotal());
        assertEquals(2L, page.getCurrent());
        assertEquals(1L, page.getSize());
        assertEquals(prefix + "-middle", page.getRecords().get(0).getCode());
        List<Goods> list = resource.list(null, prefix, "draft", new BigDecimal("15"),
                new BigDecimal("30"), "price_DESC").getData();
        assertEquals(Arrays.asList(prefix + "-high", prefix + "-middle"),
                list.stream().map(Goods::getCode).collect(Collectors.toList()));
        assertEquals(Long.valueOf(2L), resource.count(null, prefix, "draft",
                new BigDecimal("15"), new BigDecimal("30")).getData());
        assertEquals(Long.valueOf(2L), new GoodsResource(applicationService).count(null, prefix, "draft",
                new BigDecimal("15"), new BigDecimal("30")).getData());
    }

    /** \u8bf7\u6c42 <code>GET /api/goods/count</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void cdiAndHttpPreserveTheSinglePageListCountEndpoints() {
        GoodsResource resource = Arc.container().instance(GoodsResource.class).get();
        assertEquals(Long.valueOf(2L), resource.count(null, prefix, "draft",
                new BigDecimal("15"), new BigDecimal("30")).getData());
        assertEquals(prefix + "-middle", resource.page(2L, 1L, null, prefix, "draft",
                        new BigDecimal("15"), new BigDecimal("30"), "price_DESC")
                .getData().getRecords().get(0).getCode());
        assertEquals(2, resource.list(null, prefix, "draft", new BigDecimal("15"),
                new BigDecimal("30"), "price_DESC").getData().size());

        given().queryParam("current", 2).queryParam("size", 1).queryParam("nameLike", prefix)
                .queryParam("status", "draft").queryParam("priceMin", 15).queryParam("priceMax", 30)
                .queryParam("orderBys", "price_DESC").when().get("/api/goods/page")
                .then().statusCode(200).body("data.total", equalTo(2))
                .body("data.current", equalTo(2)).body("data.size", equalTo(1))
                .body("data.records", hasSize(1)).body("data.records[0].code", equalTo(prefix + "-middle"));
        given().queryParam("nameLike", prefix).queryParam("status", "draft")
                .queryParam("priceMin", 15).queryParam("priceMax", 30).queryParam("orderBys", "price_DESC")
                .when().get("/api/goods/list").then().statusCode(200).body("data", hasSize(2))
                .body("data[0].code", equalTo(prefix + "-high"));
        given().queryParam("nameLike", prefix).queryParam("status", "draft")
                .queryParam("priceMin", 15).queryParam("priceMax", 30)
                .when().get("/api/goods/count").then().statusCode(200).body("data", equalTo(2));
    }

    /**
     * \u9a8c\u8bc1<code>candidate.getParameterTypes()</code> \u4e0e\u2026\u7684\u5143\u7d20\u9010\u9879\u76f8\u540c <code>constructor.getParameterTypes()</code>\uff0c<code>injected</code> \u7b49\u4e8e <code>1</code>\uff0c<code>bindings</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void constructorParameterNamesAndHttpBindingOwnershipRemainExact() throws Exception {
        Constructor<GoodsResource> constructor = GoodsResource.class.getConstructor(
                GoodsApplicationService.class, GoodsQueryResource.class);
        assertArrayEquals(new String[]{"applicationService", "queryResource"},
                Arrays.stream(constructor.getParameters()).map(parameter -> parameter.getName()).toArray(String[]::new));
        int injected = 0;
        for (Constructor<?> candidate : GoodsResource.class.getDeclaredConstructors()) {
            boolean hasInject = Arrays.stream(candidate.getAnnotations())
                    .anyMatch(annotation -> annotation.annotationType().getName().endsWith(".inject.Inject"));
            if (hasInject) {
                injected++;
                assertArrayEquals(constructor.getParameterTypes(), candidate.getParameterTypes());
            }
        }
        assertEquals(1, injected);
        assertNames(GoodsResource.class.getMethod("page", Long.class, Long.class, String.class, String.class,
                        String.class, BigDecimal.class, BigDecimal.class, String.class),
                "current", "size", "code", "nameLike", "status", "priceMin", "priceMax", "orderBys");
        assertNames(GoodsResource.class.getMethod("list", String.class, String.class, String.class,
                        BigDecimal.class, BigDecimal.class, String.class),
                "code", "nameLike", "status", "priceMin", "priceMax", "orderBys");
        assertNames(GoodsResource.class.getMethod("count", String.class, String.class, String.class,
                BigDecimal.class, BigDecimal.class), "code", "nameLike", "status", "priceMin", "priceMax");
        for (String name : new String[]{"page", "list", "count"}) {
            int bindings = 0;
            for (Class<?> resource : new Class<?>[]{GoodsResource.class, GoodsQueryResource.class}) {
                for (Method method : resource.getDeclaredMethods()) {
                    if (method.getName().equals(name) && Arrays.stream(method.getAnnotations())
                            .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals("GET"))) {
                        bindings++;
                    }
                }
            }
            assertEquals(1, bindings, name);
        }
    }
}
