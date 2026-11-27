package io.ddd4j.web.webflux;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.spring.config.SpringCommandConfig;
import io.ddd4j.spring.config.SpringCoreConfig;
import io.ddd4j.spring.config.SpringProjectionConfig;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 真实 Tomcat 端口与 Spring HTTP 管线验证基础模式及显式投影模式。
 */
class SpringRuntimeHttpIntegrationTest {
    /**
     * \u9a8c\u8bc1<code>command.getStatusCode().value()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>command.getBody()</code> \u7b49\u4e8e <code>"accepted"</code>\u3002
     * @param directory \u4f20\u7ed9 <code>server.setBaseDir()</code> \u7684 <code>Path</code> \u8f93\u5165
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void realHttpWithBasicAndExplicitProjectionModes(@TempDir Path directory) throws Exception {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(5_000);
        requests.setReadTimeout(5_000);
        RestTemplate client = new RestTemplate(requests);
        for (boolean projection : new boolean[]{false, true}) {
            Tomcat server = new Tomcat();
            server.setBaseDir(Files.createTempDirectory(directory, "webflux").toString());
            server.setPort(0);
            server.getConnector().setProperty("address", "127.0.0.1");
            Context servletContext = server.addContext("", Files.createTempDirectory(directory, "docbase").toString());
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

            try {
                context.register(HttpConfiguration.class);
                if (projection) {
                    context.register(ProjectionConfiguration.class);
                }
                context.refresh();
                Wrapper wrapper = Tomcat.addServlet(servletContext, "spring",
                        new ServletHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build()));
                wrapper.setAsyncSupported(true);
                wrapper.setLoadOnStartup(1);
                servletContext.addServletMappingDecoded("/", "spring");
                server.start();
                String base = "http://127.0.0.1:" + server.getConnector().getLocalPort();
                ResponseEntity<String> command = client.getForEntity(base + "/r06/command", String.class);
                assertEquals(200, command.getStatusCode().value());
                assertEquals("accepted", command.getBody());
                assertEquals(1, context.getBeansOfType(CommandBus.class).size());
                assertEquals(projection ? 1 : 0, context.getBeansOfType(ViewManager.class).size());
                ResponseEntity<String> result = client.getForEntity(base + "/r06/projection", String.class);
                assertEquals(200, result.getStatusCode().value());
                assertEquals(projection ? "42:1" : "disabled", result.getBody());
                assertFalse(context.containsBean("entityManagerFactory"));
            } finally {
                server.stop();
                server.destroy();
                context.close();
            }
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>executor()</code>\u3001<code>endpoint()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Import({SpringCoreConfig.class, SpringCommandConfig.class})
    static class HttpConfiguration {
        /**
         * \u6784\u9020 <code>CommandExecutor&lt;Ping&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CommandExecutor&lt;Ping&gt;</code> \u7ed3\u679c
         */
        @Bean
        CommandExecutor<Ping> executor() {
            return new CommandExecutor<Ping>() {
                public Set<Class<? extends Command>> supportedCommands() {
                    return Collections.singleton(Ping.class);
                }

                public Result execute(Ping command) {
                    return Result.ok("accepted");
                }
            };
        }

        /**
         * \u521b\u5efa <code>new Endpoint</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @param bus \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>CommandBus</code> \u8f93\u5165
         * @param manager \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>ObjectProvider&lt;ViewManager&gt;</code> \u8f93\u5165
         * @param deliveries \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>ObjectProvider&lt;AtomicInteger&gt;</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new Endpoint(bus, manager, deliveries)</code>
         */
        @Bean
        Endpoint endpoint(CommandBus bus, ObjectProvider<ViewManager> manager, ObjectProvider<AtomicInteger> deliveries) {
            return new Endpoint(bus, manager, deliveries);
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>taskScheduler()</code>\u3001<code>deliveries()</code>\u3001<code>runner()</code>\u3001<code>view()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @Configuration(proxyBeanMethods = false)
    @Import(SpringProjectionConfig.class)
    static class ProjectionConfiguration {
        /**
         * \u521b\u5efa <code>new ThreadPoolTaskScheduler</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ThreadPoolTaskScheduler()</code>
         */
        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }

        /**
         * \u521b\u5efa <code>new AtomicInteger</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AtomicInteger()</code>
         */
        @Bean
        AtomicInteger deliveries() {
            return new AtomicInteger();
        }

        /**
         * \u8c03\u7528 <code>positions.save()</code>\u3001<code>Collections.singletonList()</code>\u3001<code>EventChunk.empty()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionRunner&lt;String&gt;</code> \u7ed3\u679c
         */
        @Bean
        ProjectionRunner<String> runner() {
            InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
            positions.save(new DefaultProjectionPosition("http-view", 41));
            return new ProjectionRunner<>(new DefaultProjectionService(positions), (stream, from, size, types) ->
                    from == 41 ? new EventChunk<>(Collections.singletonList("order-created"), 42) : EventChunk.empty(from));
        }

        /**
         * \u6784\u9020 <code>ProjectionView&lt;String&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @param deliveries \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>AtomicInteger</code> \u8f93\u5165
         * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;String&gt;</code> \u7ed3\u679c
         */
        @Bean
        ProjectionView<String> view(AtomicInteger deliveries) {
            return new ProjectionView<String>() {
                public String getName() {
                    return "http-view";
                }

                public String getCron() {
                    return "0 0 0 1 JAN *";
                }

                public Collection<String> getEventTypes() {
                    return Collections.singletonList("OrderCreated");
                }

                public void handleEvents(Collection<String> events) {
                    deliveries.addAndGet(events.size());
                }
            };
        }
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    static final class Ping implements Command {
    }

    /** \u4e3a HTTP \u5408\u540c\u63d0\u4f9b\u5b9e\u9645\u8bf7\u6c42\u8def\u7531\u7684 Spring \u6d4b\u8bd5\u63a7\u5236\u5668\u3002 */
    @RestController
    static final class Endpoint {
        /** \u6839\u636e\u547d\u4ee4\u7c7b\u578b\u5206\u53d1\u5199\u64cd\u4f5c\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
        private final CommandBus bus;
        /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
        private final ObjectProvider<ViewManager> manager;
        /** \u5df2\u4ea4\u4ed8\u7ed9\u6d4b\u8bd5\u6d88\u8d39\u8005\u6216\u5904\u7406\u5668\u7684\u6295\u9012\u8bb0\u5f55\u3002 */
        private final ObjectProvider<AtomicInteger> deliveries;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Endpoint</code> \u5b9e\u4f8b\u3002
         * @param bus \u6839\u636e\u547d\u4ee4\u7c7b\u578b\u5206\u53d1\u5199\u64cd\u4f5c\u7684\u547d\u4ee4\u603b\u7ebf
         * @param manager <code>manager</code> \u5b57\u6bb5
         * @param deliveries <code>deliveries</code> \u5b57\u6bb5
         */
        Endpoint(CommandBus bus, ObjectProvider<ViewManager> manager, ObjectProvider<AtomicInteger> deliveries) {
            this.bus = bus;
            this.manager = manager;
            this.deliveries = deliveries;
        }

        /**
         * \u901a\u8fc7 <code>bus.&lt;String&gt;execute(new Ping()).getData()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>bus.&lt;String&gt;execute(new Ping()).getData()</code>
         */
        @GetMapping("/r06/command")
        String command() {
            return bus.<String>execute(new Ping()).getData();
        }

        /**
         * \u6309 <code>(Objects.isNull(active))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>"disabled"</code>\u3001<code>active.getProjectionStatus("http-view").nextEventNumber() + ":" + deliveries.getObject().get()</code>
         */
        @GetMapping("/r06/projection")
        String projection() {
            ViewManager active = manager.getIfAvailable();
            if (Objects.isNull(active)) {
                return "disabled";
            }
            active.triggerOnce();
            return active.getProjectionStatus("http-view").nextEventNumber() + ":" + deliveries.getObject().get();
        }
    }
}
