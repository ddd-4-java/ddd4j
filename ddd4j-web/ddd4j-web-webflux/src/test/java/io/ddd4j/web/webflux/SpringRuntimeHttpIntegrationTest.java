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
     * 验证<code>command.getStatusCode().value()</code> 等于 <code>200</code>，<code>command.getBody()</code> 等于 <code>"accepted"</code>。
     *
     * @param directory 传给 <code>server.setBaseDir()</code> 的 <code>Path</code> 输入
     * @throws Exception 准备或读取夹具文件时失败
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

    /**
     * 向测试容器注册 <code>executor()</code>、<code>endpoint()</code> 等测试入口或组件。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Import({SpringCoreConfig.class, SpringCommandConfig.class})
    static class HttpConfiguration {
        /**
         * 构造 <code>CommandExecutor&lt;Ping&gt;</code> 作为当前测试的输入或结果。
         *
         * @return 提供给后续契约断言的 <code>CommandExecutor&lt;Ping&gt;</code> 结果
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
         * 创建 <code>new Endpoint</code> 供当前测试路径调用。
         *
         * @param bus        用于构造本次返回结果的 <code>CommandBus</code> 输入
         * @param manager    用于构造本次返回结果的 <code>ObjectProvider&lt;ViewManager&gt;</code> 输入
         * @param deliveries 用于构造本次返回结果的 <code>ObjectProvider&lt;AtomicInteger&gt;</code> 输入
         * @return 当前测试使用的结果，取自 <code>new Endpoint(bus, manager, deliveries)</code>
         */
        @Bean
        Endpoint endpoint(CommandBus bus, ObjectProvider<ViewManager> manager, ObjectProvider<AtomicInteger> deliveries) {
            return new Endpoint(bus, manager, deliveries);
        }
    }

    /**
     * 向测试容器注册 <code>taskScheduler()</code>、<code>deliveries()</code>、<code>runner()</code>、<code>view()</code> 等测试入口或组件。
     */
    @Configuration(proxyBeanMethods = false)
    @Import(SpringProjectionConfig.class)
    static class ProjectionConfiguration {
        /**
         * 创建 <code>new ThreadPoolTaskScheduler</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new ThreadPoolTaskScheduler()</code>
         */
        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            return new ThreadPoolTaskScheduler();
        }

        /**
         * 创建 <code>new AtomicInteger</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new AtomicInteger()</code>
         */
        @Bean
        AtomicInteger deliveries() {
            return new AtomicInteger();
        }

        /**
         * 调用 <code>positions.save()</code>、<code>Collections.singletonList()</code>、<code>EventChunk.empty()</code> 执行当前夹具操作。
         *
         * @return 提供给后续契约断言的 <code>ProjectionRunner&lt;String&gt;</code> 结果
         */
        @Bean
        ProjectionRunner<String> runner() {
            InMemoryProjectionPositionRepository positions = new InMemoryProjectionPositionRepository();
            positions.save(new DefaultProjectionPosition("http-view", 41));
            return new ProjectionRunner<>(new DefaultProjectionService(positions), (stream, from, size, types) ->
                    from == 41 ? new EventChunk<>(Collections.singletonList("order-created"), 42) : EventChunk.empty(from));
        }

        /**
         * 构造 <code>ProjectionView&lt;String&gt;</code> 作为当前测试的输入或结果。
         *
         * @param deliveries 用于构造本次返回结果的 <code>AtomicInteger</code> 输入
         * @return 提供给后续契约断言的 <code>ProjectionView&lt;String&gt;</code> 结果
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

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    static final class Ping implements Command {
    }

    /**
     * 为 HTTP 合同提供实际请求路由的 Spring 测试控制器。
     */
    @RestController
    static final class Endpoint {
        /**
         * 根据命令类型分发写操作的命令总线。
         */
        private final CommandBus bus;
        /**
         * 当前测试使用并负责释放的视图或认证管理器。
         */
        private final ObjectProvider<ViewManager> manager;
        /**
         * 已交付给测试消费者或处理器的投递记录。
         */
        private final ObjectProvider<AtomicInteger> deliveries;

        /**
         * 用给定值初始化 <code>Endpoint</code> 实例。
         *
         * @param bus        根据命令类型分发写操作的命令总线
         * @param manager    <code>manager</code> 字段
         * @param deliveries <code>deliveries</code> 字段
         */
        Endpoint(CommandBus bus, ObjectProvider<ViewManager> manager, ObjectProvider<AtomicInteger> deliveries) {
            this.bus = bus;
            this.manager = manager;
            this.deliveries = deliveries;
        }

        /**
         * 通过 <code>bus.&lt;String&gt;execute(new Ping()).getData()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>bus.&lt;String&gt;execute(new Ping()).getData()</code>
         */
        @GetMapping("/r06/command")
        String command() {
            return bus.<String>execute(new Ping()).getData();
        }

        /**
         * 按 <code>(Objects.isNull(active))</code> 等实际条件选择当前用例返回的结果。
         *
         * @return 根据测试条件返回的可能值，包括 <code>"disabled"</code>、<code>active.getProjectionStatus("http-view").nextEventNumber() + ":" + deliveries.getObject().get()</code>
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
