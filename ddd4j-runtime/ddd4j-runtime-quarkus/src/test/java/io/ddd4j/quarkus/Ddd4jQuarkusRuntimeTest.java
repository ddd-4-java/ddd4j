package io.ddd4j.quarkus;

import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;

class Ddd4jQuarkusRuntimeTest extends AbstractRuntimeContractTest {

    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jQuarkusRuntime runtime = new Ddd4jQuarkusRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }
}
