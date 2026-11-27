package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.dropwizard.Ddd4jLogbackAccessRequestLogFactory;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebBundle;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dropwizard 四表商品交易及快照投影。
 */
public final class DropwizardCqrsApplication extends Application<DropwizardCqrsConfiguration> {
    /**
     * 消费商品交易领域事件的外部监听回调。
     */
    private final List<Consumer<Object>> listeners;

    /**
     * 构造没有外部事件监听器的 CQRS 示例应用。
     */
    public DropwizardCqrsApplication() {
        this.listeners = Collections.emptyList();
    }

    /**
     * 创建向指定测试或应用监听器转发交易事件的 CQRS 应用。
     *
     * @param listener 接收当前示例发布的交易事件的监听器
     */
    DropwizardCqrsApplication(Consumer<Object> listener) {
        this.listeners = Collections.singletonList(listener);
    }

    /**
     * 将命令行参数交给 Dropwizard，运行商品交易 CQRS 示例。
     *
     * @param args 应用启动参数
     * @throws Exception Dropwizard 应用初始化或启动失败
     */
    public static void main(String[] args) throws Exception {
        new DropwizardCqrsApplication().run(args);
    }

    /**
     * 在 Dropwizard 引导阶段添加商品交易示例所需的组件。
     *
     * @param bootstrap 当前 Dropwizard 应用的引导对象
     */
    @Override
    public void initialize(Bootstrap<DropwizardCqrsConfiguration> bootstrap) {
        bootstrap.addBundle(new Ddd4jBundle<>(Collections.emptyList(), listeners, null, I18nProvider.DEFAULT));
        bootstrap.addBundle(new Ddd4jDropwizardWebBundle<DropwizardCqrsConfiguration>(configuration -> {
            var web = new Ddd4jDropwizardWebConfiguration();
            web.setDefaultAuthenticationMode(AuthenticationMode.DISABLED);
            web.setIdempotencyEnabled(false);
            return web;
        }));
    }

    /**
     * 从配置装配商品交易应用并向 Dropwizard 环境注册资源。
     *
     * @param config      商品交易示例的数据库及服务配置
     * @param environment 用于登记路由、过滤器及生命周期的框架环境
     */
    @Override
    public void run(DropwizardCqrsConfiguration config, Environment environment) {
        Ddd4jLogbackAccessRequestLogFactory.configure(config);
        var api = StrKit.isBlank(config.getCatalogJdbcUrl()) ? CatalogRuntime.start()
                : CatalogRuntime.start(config.getCatalogJdbcUrl(), config.getCatalogDbUser(), config.getCatalogDbPassword());
        environment.jersey().register(new CatalogTradeResource(api));
    }
}
