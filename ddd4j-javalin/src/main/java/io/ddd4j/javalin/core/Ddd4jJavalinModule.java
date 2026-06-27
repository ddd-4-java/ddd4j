package io.ddd4j.javalin.core;

import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.ddd4j.core.contract.DomainEventPublisher;
import io.ddd4j.core.context.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * ddd4j Javalin Guice Module
 * <p>
 * 用户在自己的 Guice Injector 中 install(this) 即可启用 ddd4j 三个核心 SPI。
 *
 * <pre>{@code
 * Injector injector = Guice.createInjector(new Ddd4jJavalinModule());
 * DomainEventPublisher publisher = injector.getInstance(DomainEventPublisher.class);
 * }</pre>
 *
 * @author Loong Wan
 * @since 3.4.x
 */
public class Ddd4jJavalinModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
        bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
        bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public EventBus eventBus() {
        return new EventBus();
    }
}
