package io.ddd4j.helidon;

import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;

/** Helidon \u751f\u547d\u5468\u671f\u548c\u5c31\u7eea\u72b6\u6001\u7684\u8fd0\u884c\u65f6\u5951\u7ea6\u3002 */
class Ddd4jHelidonRuntimeTest extends AbstractRuntimeContractTest {

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
        Ddd4jHelidonRuntime runtime = new Ddd4jHelidonRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }
}
