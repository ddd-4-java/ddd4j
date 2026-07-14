package io.ddd4j.vertx;

import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import io.vertx.core.Vertx;

class Ddd4jVertxRuntimeTest extends AbstractRuntimeContractTest {

    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Vertx vertx = Vertx.vertx();
        Ddd4jVertxRuntime runtime = new Ddd4jVertxRuntime(vertx, fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus());
        return new RuntimeContractAdapter(runtime::start, () -> {
            runtime.close();
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }, fixtures.services());
    }
}
