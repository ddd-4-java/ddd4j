package io.ddd4j.web.webmvc;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.spring.config.SpringCommandConfig;
import io.ddd4j.spring.config.SpringCoreConfig;
import io.ddd4j.spring.config.SpringProjectionConfig;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 真实 Tomcat 端口与 Spring HTTP 管线验证基础模式及显式投影模式。
 */
class SpringRuntimeHttpIntegrationTest {
    @Test
    void realHttpWithBasicAndExplicitProjectionModes() throws Exception {
        for (boolean projection : new boolean[]{false, true}) {
            Tomcat server = new Tomcat();
            server.setBaseDir(Files.createTempDirectory("r06-webmvc").toString());
            server.setPort(0);
            server.getConnector();
            org.apache.catalina.Context servletContext = server.addContext("", Files.createTempDirectory("r06-docbase").toString());
            org.springframework.web.context.support.AnnotationConfigWebApplicationContext context = new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();
            context.setServletContext(servletContext.getServletContext());
            try {
                context.register(HttpConfiguration.class);
                if (projection) {
                    context.register(ProjectionConfiguration.class);
                }
                context.refresh();
                var wrapper = Tomcat.addServlet(servletContext, "spring", new org.springframework.web.servlet.DispatcherServlet(context));
                wrapper.setAsyncSupported(true);
                wrapper.setLoadOnStartup(1);
                servletContext.addServletMappingDecoded("/", "spring");
                server.start();
                String base = "http://127.0.0.1:" + server.getConnector().getLocalPort();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> command = client.send(HttpRequest.newBuilder(URI.create(base + "/r06/command")).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, command.statusCode());
                assertEquals("accepted", command.body());
                assertEquals(1, context.getBeansOfType(CommandBus.class).size());
                assertEquals(projection ? 1 : 0, context.getBeansOfType(ViewManager.class).size());
                HttpResponse<String> result = client.send(HttpRequest.newBuilder(URI.create(base + "/r06/projection")).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, result.statusCode());
                assertEquals(projection ? "42:1" : "disabled", result.body());
                assertFalse(context.containsBean("entityManagerFactory"));
            } finally {
                server.stop();
                server.destroy();
                context.close();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SpringCoreConfig.class, SpringCommandConfig.class})
    static class HttpConfiguration {
        @Bean
        CommandExecutor<Ping> executor() {
            return new CommandExecutor<Ping>() {
                public Set<Class<? extends Command>> supportedCommands() {
                    return Set.of(Ping.class);
                }

                public Result execute(Ping command) {
                    return Result.ok("accepted");
                }
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
        ThreadPoolTaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }

        @Bean
        AtomicInteger deliveries() {
            return new AtomicInteger();
        }

        @Bean
        ProjectionRunner<String> runner() {
            InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
            positions.save(new DefaultProjectionPosition("http-view", 41));
            return new ProjectionRunner<>(new DefaultProjectionService(positions), (stream, from, size, types) ->
                    from == 41 ? new EventChunk<>(List.of("order-created"), 42) : EventChunk.empty(from));
        }

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
                    return List.of("OrderCreated");
                }

                public void handleEvents(Collection<String> events) {
                    deliveries.addAndGet(events.size());
                }
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
        String command() {
            return bus.<String>execute(new Ping()).getData();
        }

        @GetMapping("/r06/projection")
        String projection() {
            ViewManager active = manager.getIfAvailable();
            if (java.util.Objects.isNull(active)) {
                return "disabled";
            }
            active.triggerOnce();
            return active.getProjectionStatus("http-view").nextEventNumber() + ":" + deliveries.getObject().get();
        }
    }
}
