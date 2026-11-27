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
    private final List<Goods> seeded = new ArrayList<>();
    private GoodsApplicationService applicationService;
    private GoodsQueryResource queryResource;
    private InMemoryGoodsRepository repository;
    private String prefix;

    private static void assertNames(Method method, String... expected) {
        assertArrayEquals(expected, Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getName()).toArray(String[]::new));
    }

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

    @AfterEach
    void removeOnlyOurRows() {
        if (Objects.nonNull(repository)) {
            for (Goods goods : seeded) {
                repository.deleteById(goods.getId());
            }
        }
    }

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
