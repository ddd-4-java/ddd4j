package io.ddd4j.web.webflux;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.spring.config.SpringCoreConfig;
import io.ddd4j.spring.config.SpringCommandConfig;
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

/** 真实 Tomcat 端口与 Spring HTTP 管线验证基础模式及显式投影模式。 */
class SpringRuntimeHttpIntegrationTest {
    @Test
    void realHttpWithBasicAndExplicitProjectionModes(@TempDir Path directory) throws Exception {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(5_000);
        requests.setReadTimeout(5_000);
        RestTemplate client = new RestTemplate(requests);
        for (boolean projection : new boolean[] {false, true}) {
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

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Import({SpringCoreConfig.class, SpringCommandConfig.class})
    static class HttpConfiguration {
        @Bean
        CommandExecutor<Ping> executor() {
            return new CommandExecutor<Ping>() {
                public Set<Class<? extends Command>> supportedCommands() { return Collections.singleton(Ping.class); }
                public Result execute(Ping command) { return Result.ok("accepted"); }
            };
        }
        @Bean
        Endpoint endpoint(CommandBus bus, ObjectProvider<ViewManager> manager, ObjectProvider<AtomicInteger> deliveries) {
            return new Endpoint(bus, manager, deliveries);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SpringProjectionConfig.class)
    static class ProjectionConfiguration {
        @Bean
        ThreadPoolTaskScheduler taskScheduler() { return new ThreadPoolTaskScheduler(); }
        @Bean
        AtomicInteger deliveries() { return new AtomicInteger(); }
        @Bean
        ProjectionRunner<String> runner() {
            InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
            positions.save(new DefaultProjectionPosition("http-view", 41));
            return new ProjectionRunner<>(new DefaultProjectionService(positions), (stream, from, size, types) ->
                    from == 41 ? new EventChunk<>(Collections.singletonList("order-created"), 42) : EventChunk.empty(from));
        }
        @Bean
        ProjectionView<String> view(AtomicInteger deliveries) {
            return new ProjectionView<String>() {
                public String getName() { return "http-view"; }
                public String getCron() { return "0 0 0 1 JAN *"; }
                public Collection<String> getEventTypes() { return Collections.singletonList("OrderCreated"); }
                public void handleEvents(Collection<String> events) { deliveries.addAndGet(events.size()); }
            };
        }
    }

    static final class Ping implements Command {
    }

    @RestController
    static final class Endpoint {
        private final CommandBus bus;
        private final ObjectProvider<ViewManager> manager;
        private final ObjectProvider<AtomicInteger> deliveries;
        Endpoint(CommandBus bus, ObjectProvider<ViewManager> manager, ObjectProvider<AtomicInteger> deliveries) {
            this.bus = bus;
            this.manager = manager;
            this.deliveries = deliveries;
        }
        @GetMapping("/r06/command")
        String command() { return bus.<String>execute(new Ping()).getData(); }
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
