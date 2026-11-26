package io.ddd4j.sample.quarkus.satoken.rbac;

import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.MapKit;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.EventContext;
import javax.enterprise.inject.spi.EventMetadata;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.inject.Inject;
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

    @Inject
    RbacConfig rbac;

    private static <T> ObserverMethod<? super T> observer(T event) {
        return CDI.current().getBeanManager().resolveObserverMethods(event).stream()
                .filter(observer -> observer.getBeanClass() == RbacConfig.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Arc must register the RBAC "
                        + event.getClass().getSimpleName() + " lifecycle observer"));
    }

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

    public static final class OwnershipProfile implements QuarkusTestProfile {
    }
}
