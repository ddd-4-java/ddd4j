package io.ddd4j.sample.javalin;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.spi.AnonymousSubjectProvider;
import io.ddd4j.sample.javalin.spi.DefaultI18nProvider;
import io.ddd4j.sample.javalin.spi.NoOpDomainEventPublisher;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

/**
 * ddd4j 在 Javalin 框架下的最小启动示例。
 *
 * <h3>替代品对照</h3>
 * <table border="1">
 *   <caption>ddd4j 在 4 个 Web 框架中的适配策略</caption>
 *   <tr><th>框架</th><th>是否有 DI 容器</th><th>是否有 ddd4j runtime 模块</th><th>SPI 注入方式</th></tr>
 *   <tr><td>Spring</td><td>有（{@code ApplicationContext}）</td><td>有（{@code ddd4j-runtime-spring}）</td>
 *       <td>容器启动期扫描 {@code @Bean} 注入 BaseContext</td></tr>
 *   <tr><td>Quarkus</td><td>有（CDI）</td><td>有（{@code ddd4j-runtime-quarkus}）</td>
 *       <td>CDI Observer 启动期注入 BaseContext</td></tr>
 *   <tr><td>Guice</td><td>有（{@code Injector}）</td><td>有（{@code ddd4j-runtime-guice}）</td>
 *       <td>{@code Ddd4jGuiceModule#configure} 注入 BaseContext</td></tr>
 *   <tr><td>Javalin</td><td><b>无</b></td><td><b>无</b></td>
 *       <td>业务方在 main 里手动 {@code BaseContext.inject}</td></tr>
 * </table>
 *
 * <h3>运行</h3>
 * <pre>{@code
 * mvn -pl ddd4j-samples/ddd4j-sample-javalin exec:java -Dexec.mainClass=io.ddd4j.sample.javalin.JavalinSample
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.0
 */
@Slf4j
public class JavalinSample {

    public static void main(String[] args) {

        // 1. 业务方准备核心 SPI 实例（这里用 sample 内的 NoOp 示例实现）
        DomainEventPublisher domainEventPublisher = new NoOpDomainEventPublisher();
        SubjectProvider subjectProvider = new AnonymousSubjectProvider();
        I18nProvider i18nProvider = new DefaultI18nProvider();

        // 2. 启动前一次性把 SPI 注入到 JVM 级 BaseContext（Javalin 无 DI 容器，全局 Map 是最简实现）
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainEventPublisher);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);

        // 3. 启动 Javalin（路由注册方式见 Javalin 官方文档，本 sample 重点在 SPI 注入）
        Javalin app = Javalin.create();

        // 业务代码内部统一通过 io.ddd4j.core.context.Contexts.inject(...) 查 SPI，零框架耦合
        // 示例：演示 SPI 查找（线程级 → 全局级）
        DomainEventPublisher publisher = Contexts.getOrThrow(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        log.info("[Bootstrap] DomainEventPublisher = {}", publisher.getClass().getSimpleName());

        app.start(7000);
        log.info("Javalin started on http://localhost:7000");
    }
}
