package io.ddd4j.quarkus;

import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;

/** \u5b9e\u73b0 <code>AbstractRuntimeContractTest</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>createRuntime()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
class Ddd4jQuarkusRuntimeTest extends AbstractRuntimeContractTest {

    /**
     * 为契约测试构造当前框架的运行实例。
     *
     * <p>测试夹具会调用 <code>fixtures.publisher()</code>、<code>fixtures.subjectProvider()</code>。</p>
     *
     * @return 待验证的运行时适配器
     */
    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jQuarkusRuntime runtime = new Ddd4jQuarkusRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }
}
