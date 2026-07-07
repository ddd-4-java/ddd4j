package io.ddd4j.sample.javalin;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.event.MQEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.spi.AnonymousSubjectProvider;
import io.ddd4j.sample.javalin.spi.DefaultI18nProvider;
import io.ddd4j.sample.javalin.spi.NoOpDomainEventPublisher;
import io.ddd4j.sample.javalin.spi.NoOpMQEventPublisher;
import io.javalin.Javalin;

/**
 * ddd4j 在 Javalin 框架下的最小启动示例。
 *
 * <h3>核心要点</h3>
 * <p>Javalin 本身<b>没有 DI 容器</b>，因此 ddd4j 不提供独立 runtime 模块。
 * 业务方只需要在 Javalin 启动时，把 4 个核心 SPI 实例手动写入
 * {@link BaseContext} 即可——这也是所有 ddd4j 业务代码查找 SPI 的统一入口。
 *
 * <h3>为什么不需要 ddd4j-runtime-javalin 模块</h3>
 * <p>把 SPI 注入到 {@link BaseContext} 这件事，本质上就是 4 行
 * {@code BaseContext.inject(key, type, instance)} 调用。任何基于 Javalin
 * 的工程都可以在自己的 main 方法里完成这件事，没有必要、也不应该有专门模块。
 * Spring / Quarkus / Guice 才需要独立 runtime 模块，因为它们要在容器启动期
 * <b>反射拿 SPI Bean</b>；Javalin 不存在容器抽象，所以也省掉了这一层。
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
public class JavalinSample {

    public static void main(String[] args) {

        // 1. 业务方自己准备 4 个 SPI 实例（这里用 sample 内的 NoOp 示例实现）
        DomainEventPublisher domainEventPublisher = new NoOpDomainEventPublisher();
        MQEventPublisher mqEventPublisher = new NoOpMQEventPublisher();
        SubjectProvider subjectProvider = new AnonymousSubjectProvider();
        I18nProvider i18nProvider = new DefaultI18nProvider();

        // 2. 启动前一次性把 SPI 注入到 JVM 级 BaseContext（Javalin 无 DI 容器，全局 Map 是最简实现）
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainEventPublisher);
        BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, mqEventPublisher);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);

        // 3. 启动 Javalin（路由注册方式见 Javalin 官方文档，本 sample 重点在 SPI 注入）
        Javalin app = Javalin.create();

        // 业务代码内部统一通过 io.ddd4j.core.context.Contexts.inject(...) 查 SPI，零框架耦合
        // 示例：演示 SPI 查找（线程级 → 全局级）
        DomainEventPublisher publisher = Contexts.getOrThrow(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        System.out.println("[Bootstrap] DomainEventPublisher = " + publisher.getClass().getSimpleName());

        app.start(7000);
        System.out.println("Javalin started on http://localhost:7000");
    }
}