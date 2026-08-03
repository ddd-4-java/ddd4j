package io.ddd4j.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;

class GuiceRuntimeContractTest extends AbstractRuntimeContractTest {

    @Override
    protected RuntimeContract createRuntime() {
        Injector injector = Guice.createInjector(new Ddd4jGuiceModule());
        Ddd4jGuiceRuntime runtime = injector.getInstance(Ddd4jGuiceRuntime.class);
        return new RuntimeContractAdapter(runtime::start, runtime::close, new RuntimeFixtures().services(),
                () -> runtime.readiness().readiness());
    }
}
