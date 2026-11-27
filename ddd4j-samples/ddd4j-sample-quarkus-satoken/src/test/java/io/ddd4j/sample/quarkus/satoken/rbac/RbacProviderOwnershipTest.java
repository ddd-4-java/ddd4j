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

    /** \u5f53\u524d\u793a\u4f8b\u7528\u6765\u89e3\u6790\u89d2\u8272\u7f16\u8f91\u548c\u6743\u9650\u5206\u914d\u7684\u914d\u7f6e\u3002 */
    @Inject
    RbacConfig rbac;

    /**
     * \u8c03\u7528 <code>CDI.current()</code>\u3001<code>observer.getBeanClass()</code>\u3001<code>event.getClass()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ObserverMethod&lt;? super T&gt;</code> \u7ed3\u679c
     */
    private static <T> ObserverMethod<? super T> observer(T event) {
        return CDI.current().getBeanManager().resolveObserverMethods(event).stream()
                .filter(observer -> observer.getBeanClass() == RbacConfig.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Arc must register the RBAC "
                        + event.getClass().getSimpleName() + " lifecycle observer"));
    }

    /**
     * \u8c03\u7528 <code>observer.notify()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param observer \u63a5\u6536\u6d4b\u8bd5\u72b6\u6001\u6216\u6e05\u7406\u7ed3\u679c\u7684\u89c2\u5bdf\u56de\u8c03
     * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
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

    /** \u9a8c\u8bc1<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>caller</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\u3002 */
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

    /** \u542f\u7528\u8d44\u6e90\u6240\u6709\u6743\u4e0e\u4e0a\u4e0b\u6587\u6e05\u7406\u5408\u540c\u6240\u9700\u7684 Quarkus \u6d4b\u8bd5\u914d\u7f6e\u3002 */
    public static final class OwnershipProfile implements QuarkusTestProfile {
    }
}
