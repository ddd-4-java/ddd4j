/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.qlexpress;

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

        assertThat(engine.validate("if (").valid()).isFalse();
        assertThat(engine.validate(" ").message()).isEqualTo("表达式不能为空");
    }

    @Test
    void safeExecutionShouldCaptureFailure() {
        QLExpressEngine engine = QLExpress.create();

        QLExpressExecutionResult<Object> result = engine.executeSafely("missing + 1", Map.of());

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

        assertThat(engine.execute("multiply(5)", Map.of())).isEqualTo(10);

        engine.registerOrReplaceFunction(new MultiplierFunction(3));
        assertThat(engine.execute("multiply(5)", Map.of())).isEqualTo(15);

        assertThat(engine.removeFunction("multiply")).isTrue();
        assertThat(engine.executeSafely("multiply(5)", Map.of()).success()).isFalse();
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
