package io.ddd4j.extension.qlexpress;

import java.util.Collections;
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import io.ddd4j.extension.qlexpress.function.NamedQLFunction;
import io.ddd4j.extension.qlexpress.model.QLExpressExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QLExpressEngineTest {

    @Test
    void executeShouldReturnExpressionValueInsteadOfNativeWrapper() {
        QLExpressEngine engine = QLExpress.create();

        Object result = engine.execute("price * quantity", Map.of("price", 20, "quantity", 3));

        assertThat(result).isEqualTo(60);
    }

    @Test
    void builtInFunctionsShouldBeAvailableByDefault() {
        QLExpressEngine engine = QLExpress.create();

        Boolean result = engine.execute(
                "contains(name, 'ddd4j') && startsWith(name, 'hello')",
                Map.of("name", "hello ddd4j"),
                Boolean.class);

        assertThat(result).isTrue();
    }

    @Test
    void invalidExpressionShouldReturnValidationFailure() {
        QLExpressEngine engine = QLExpress.create();

assertThat(engine.validate("if (").valid()).isFalse());
        assertThat(engine.validate(" ").message()).isEqualTo("表达式不能为空");
    }

    @Test
    void safeExecutionShouldCaptureFailure() {
        QLExpressEngine engine = QLExpress.create();

        QLExpressExecutionResult<Object> result = engine.executeSafely("missing + 1", Collections.emptyMap());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isNotBlank();
        assertThat(result.elapsedNanos()).isPositive();
    }

    @Test
    void functionReplacementAndRemovalShouldSwitchRunnerSnapshot() {
        QLExpressEngine engine = QLExpress.builder()
                .builtInFunctions(false)
                .function(new MultiplierFunction(2))
                .build();

        assertThat(engine.execute("multiply(5)", Collections.emptyMap())).isEqualTo(10);

        engine.registerOrReplaceFunction(new MultiplierFunction(3));
        assertThat(engine.execute("multiply(5)", Collections.emptyMap())).isEqualTo(15);

        assertThat(engine.removeFunction("multiply")).isTrue();
        assertThat(engine.executeSafely("multiply(5)", Collections.emptyMap()).success()).isFalse();
    }

    @Test
    void expressionAnalysisShouldExposeExternalInputs() {
        QLExpressEngine engine = QLExpress.create();

        assertThat(engine.getExternalVariables("price * quantity"))
                .containsExactlyInAnyOrder("price", "quantity");
    }

    private static final class MultiplierFunction implements NamedQLFunction {

        private final int multiplier;

        private MultiplierFunction(int multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public String name() {
            return "multiply";
        }

        @Override
        public Object call(QContext qContext, Parameters parameters) {
            return ((Number) parameters.getValue(0)).intValue() * multiplier;
        }
    }
}
