package io.ddd4j.dropwizard;

import ch.qos.logback.classic.LoggerContext;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logback 升级后默认访问日志、禁用配置与自定义扩展仍保持原有契约。
 */
class Ddd4jLogbackAccessRequestLogFactoryTest {

    /** \u9a8c\u8bc1<code>compatible</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>original</code>\uff0c<code>compatible.getAppenders()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>original.getAppenders()</code>\uff0c<code>server.getRequestLogFactory()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>compatible</code>\u3002 */
    @Test
    void replacesDefaultFactoryAndPreservesAppenders() {
        Configuration configuration = new Configuration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory original = (LogbackAccessRequestLogFactory) server.getRequestLogFactory();

        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);

        Ddd4jLogbackAccessRequestLogFactory compatible = assertInstanceOf(
                Ddd4jLogbackAccessRequestLogFactory.class, server.getRequestLogFactory());
        assertNotSame(original, compatible);
        assertSame(original.getAppenders(), compatible.getAppenders());
        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);
        assertSame(compatible, server.getRequestLogFactory());
    }

    /** \u9a8c\u8bc1<code>((LogbackAccessRequestLogFactory) server.getRequestLogFactory()).getAppenders().isEmpty()</code>\u4e3a\u771f\uff0c<code>server.getRequestLogFactory().isEnabled()</code> \u7b49\u4e8e <code>original.isEnabled()</code>\u3002 */
    @Test
    void keepsDisabledAccessLoggingDisabled() {
        Configuration configuration = new Configuration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory original = new LogbackAccessRequestLogFactory();
        original.setAppenders(List.of());
        server.setRequestLogFactory(original);

        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);

        assertTrue(((LogbackAccessRequestLogFactory) server.getRequestLogFactory()).getAppenders().isEmpty());
        assertEquals(original.isEnabled(), server.getRequestLogFactory().isEnabled());
    }

    /** \u9a8c\u8bc1<code>server.getRequestLogFactory()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>custom</code>\u3002 */
    @Test
    void preservesCustomRequestLogFactory() {
        Configuration configuration = new Configuration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory custom = new LogbackAccessRequestLogFactory() {
        };
        server.setRequestLogFactory(custom);

        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);

        assertSame(custom, server.getRequestLogFactory());
    }

    /** \u9a8c\u8bc1<code>layout.isStarted()</code>\u4e3a\u771f\u3002 */
    @Test
    void startsDefaultLayoutWithoutRemovedClassicConverterMap() {
        LoggerContext context = new LoggerContext();
        Ddd4jLogbackAccessRequestLayout layout = new Ddd4jLogbackAccessRequestLayout(
                context, TimeZone.getTimeZone("UTC"));
        try {
            assertDoesNotThrow(layout::start);
            assertTrue(layout.isStarted());
            assertEquals("%h %l %u [%t{dd/MMM/yyyy:HH:mm:ss Z,UTC}] \"%r\" %s %b "
                    + "\"%i{Referer}\" \"%i{User-Agent}\" %D", layout.getPattern());
        } finally {
            layout.stop();
            context.stop();
        }
    }
}
