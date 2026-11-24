package io.ddd4j.sample.quarkus;

import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证实际基础 HTTP 入口无需令牌、命令执行器或 Quartz。 */
@QuarkusTest
class BasicOrderHttpTest {
    @TestHTTPResource URI base;
    @Test void sharedOrderIsDirectlyCallableWithoutAuthentication() {
        SampleContractAssertions.defaultOrder(new EnvelopeOrderHttpDriver(
                base.toString().replaceAll("/$", ""), "/api/orders", Collections.emptyMap()), "Q-BASIC-" + UUID.randomUUID());
    }
    @Test void quartzIsNotPartOfBasicApplication() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.quartz.Scheduler"));
    }
}
