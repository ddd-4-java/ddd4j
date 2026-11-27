package io.ddd4j.sample.javalin;

import io.ddd4j.core.context.SpiRegistrationScope;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线 Javalin 应用句柄的值方法与 token 日志契约。
 */
class JavalinApplicationContractTest {

    /** \u9a8c\u8bc1<code>right</code> \u7b49\u4e8e <code>left</code>\uff0c<code>right.hashCode()</code> \u7b49\u4e8e <code>left.hashCode()</code>\uff0c<code>different</code> \u4e0d\u7b49\u4e8e <code>left</code>\u3002 */
    @Test
    void sameRuntimeReferencesAndTokenHaveSameValue() {
        Javalin app = Javalin.create();
        SpiRegistrationScope scope = new SpiRegistrationScope();
        JavalinSample.JavalinApplication left = new JavalinSample.JavalinApplication(app, "token-1", scope);
        JavalinSample.JavalinApplication right = new JavalinSample.JavalinApplication(app, "token-1", scope);
        JavalinSample.JavalinApplication different = new JavalinSample.JavalinApplication(app, "token-2", scope);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
    }

    /** \u9a8c\u8bc1<code>application.toString().contains("secret-token-123")</code>\u4e3a\u5047\u3002 */
    @Test
    void stringRepresentationDoesNotRevealToken() {
        JavalinSample.JavalinApplication application = new JavalinSample.JavalinApplication(
                Javalin.create(), "secret-token-123", new SpiRegistrationScope());
        assertFalse(application.toString().contains("secret-token-123"));
    }
}
