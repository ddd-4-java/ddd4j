package io.ddd4j.dropwizard;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.i18n.I18nProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** 校验三线 Bundle 对无效服务集合的共同构造契约。 */
class Ddd4jBundleCollectionContractTest {

    @Test
    void constructorRejectsNullElementsInServiceCollections() {
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(
                Collections.<CommandExecutor<?>>singletonList(null), Collections.<Consumer<Object>>emptyList(),
                null, I18nProvider.DEFAULT, Collections.<ReadinessContributor>emptyList()));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(
                Collections.<CommandExecutor<?>>emptyList(), Collections.<Consumer<Object>>singletonList(null),
                null, I18nProvider.DEFAULT, Collections.<ReadinessContributor>emptyList()));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(
                Collections.<CommandExecutor<?>>emptyList(), Collections.<Consumer<Object>>emptyList(),
                null, I18nProvider.DEFAULT, Collections.<ReadinessContributor>singletonList(null)));
    }
}
