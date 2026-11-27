package io.ddd4j.sample.javalin;

import io.ddd4j.core.context.SpiRegistrationScope;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线 Javalin 应用句柄的值方法与 token 日志契约。
 */
class JavalinApplicationContractTest {

    /**
     * 验证<code>right</code> 等于 <code>left</code>，<code>right.hashCode()</code> 等于 <code>left.hashCode()</code>，<code>different</code> 不等于 <code>left</code>。
     */
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

    /**
     * 验证<code>application.toString().contains("secret-token-123")</code>为假。
     */
    @Test
    void stringRepresentationDoesNotRevealToken() {
        JavalinSample.JavalinApplication application = new JavalinSample.JavalinApplication(
                Javalin.create(), "secret-token-123", new SpiRegistrationScope());
        assertFalse(application.toString().contains("secret-token-123"));
    }
}
