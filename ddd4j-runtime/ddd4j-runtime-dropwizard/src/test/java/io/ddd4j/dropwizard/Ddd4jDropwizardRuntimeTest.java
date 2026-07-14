package io.ddd4j.dropwizard;

import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;

class Ddd4jDropwizardRuntimeTest extends AbstractRuntimeContractTest {

    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jDropwizardRuntime runtime = new Ddd4jDropwizardRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services());
    }
}
