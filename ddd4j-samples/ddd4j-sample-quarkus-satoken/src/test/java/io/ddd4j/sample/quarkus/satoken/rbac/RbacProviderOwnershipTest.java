package io.ddd4j.sample.quarkus.satoken.rbac;

import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.MapKit;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 通过 Arc 注册的原生观察器复验权限数据源的生命周期。
 */
@QuarkusTest
@TestProfile(RbacProviderOwnershipTest.OwnershipProfile.class)
class RbacProviderOwnershipTest {

    /**
     * 当前示例用来解析角色编辑和权限分配的配置。
     */
    @Inject
    RbacConfig rbac;

    /**
     * 调用 <code>CDI.current()</code>、<code>observer.getBeanClass()</code>、<code>event.getClass()</code> 执行当前夹具操作。
     *
     * @param <T>   当前契约测试所使用的 Java 类型
     * @param event 交给被测入口处理的测试领域事件
     * @return 提供给后续契约断言的 <code>ObserverMethod&lt;? super T&gt;</code> 结果
     */
    private static <T> ObserverMethod<? super T> observer(T event) {
        return CDI.current().getBeanManager().resolveObserverMethods(event).stream()
                .filter(observer -> observer.getBeanClass() == RbacConfig.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Arc must register the RBAC "
                        + event.getClass().getSimpleName() + " lifecycle observer"));
    }

    /**
     * 调用 <code>observer.notify()</code> 执行当前夹具操作。
     *
     * @param <T>      当前契约测试所使用的 Java 类型
     * @param observer 接收测试状态或清理结果的观察回调
     * @param event    交给被测入口处理的测试领域事件
     */
    private static <T> void notifyObserver(ObserverMethod<T> observer, T event) {
        observer.notify(new EventContext<T>() {
            @Override
            public T getEvent() {
                return event;
            }

            @Override
            public EventMetadata getMetadata() {
                return null;
            }
        });
    }

    /**
     * 验证<code>SubjectKit.dataProvider</code> 与…为不同的实例 <code>caller</code>，<code>SubjectKit.dataProvider</code> 与…为同一实例 <code>caller</code>，<code>SubjectKit.dataProvider</code> 与…为同一实例 <code>replacement</code>。
     */
    @Test
    void shutdownRestoresPreviousProviderAndPreservesAReplacement() {
        StartupEvent startup = new StartupEvent();
        ShutdownEvent shutdown = new ShutdownEvent();
        ObserverMethod<? super StartupEvent> start = observer(startup);
        ObserverMethod<? super ShutdownEvent> stop = observer(shutdown);
        SubjectDataProvider surrounding = SubjectKit.dataProvider;
        SubjectDataProvider caller = SubjectDataProvider.DEFAULT;
        SubjectDataProvider replacement = new SubjectDataProvider() {
        };
        try {
            SubjectKit.setDataProvider(caller);
            notifyObserver(start, startup);
            assertNotSame(caller, SubjectKit.dataProvider);

            rbac.reset();
            String token = given().contentType("application/json")
                    .body(MapKit.builder("username", "admin").put("password", "123456").build())
                    .post("/auth/login").then().statusCode(200).extract().path("token");
            given().header("Authorization", "Bearer " + token).contentType("application/json")
                    .post("/auth/orders/{id}/pay", "rbac-ownership").then().statusCode(200)
                    .body("data.paid", equalTo(true));
            notifyObserver(stop, shutdown);
            assertSame(caller, SubjectKit.dataProvider);

            notifyObserver(start, startup);
            SubjectKit.setDataProvider(replacement);
            notifyObserver(stop, shutdown);
            assertSame(replacement, SubjectKit.dataProvider);
        } finally {
            SubjectKit.setDataProvider(surrounding);
        }
    }

    /**
     * 启用资源所有权与上下文清理合同所需的 Quarkus 测试配置。
     */
    public static final class OwnershipProfile implements QuarkusTestProfile {
    }
}
