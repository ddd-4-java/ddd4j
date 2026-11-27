package io.ddd4j.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;

/** Guice \u9002\u914d\u5668\u7684\u8fd0\u884c\u65f6\u542f\u52a8\u3001\u5173\u95ed\u53ca\u5c31\u7eea\u5951\u7ea6\u3002 */
class GuiceRuntimeContractTest extends AbstractRuntimeContractTest {

    /**
     * 为契约测试构造当前框架的运行实例。
     *
     * <p>测试夹具会调用 <code>Guice.createInjector(new Ddd4jGuiceModule())</code>、<code>injector.getInstance(Ddd4jGuiceRuntime.class)</code>。</p>
     *
     * @return 待验证的运行时适配器
     */
    @Override
    protected RuntimeContract createRuntime() {
        Injector injector = Guice.createInjector(new Ddd4jGuiceModule());
        Ddd4jGuiceRuntime runtime = injector.getInstance(Ddd4jGuiceRuntime.class);
        return new RuntimeContractAdapter(runtime::start, runtime::close, new RuntimeFixtures().services(),
                () -> runtime.readiness().readiness());
    }
}
