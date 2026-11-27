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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** \u9a8c\u8bc1 <code>QLExpressEngine</code> \u5728<code>execute</code>\u3001<code>builtInFunctions</code>\u3001<code>invalidExpression</code>\u3001<code>safeExecution</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class QLExpressEngineTest {

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>60</code>\u3002 */
    @Test
    void executeShouldReturnExpressionValueInsteadOfNativeWrapper() {
        QLExpressEngine engine = QLExpress.create();

        Object result = engine.execute("price * quantity", new java.util.LinkedHashMap<String, Object>() {{
            put("price", 20);
            put("quantity", 3);
        }});

        assertThat(result).isEqualTo(60);
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\u3002 */
    @Test
    void builtInFunctionsShouldBeAvailableByDefault() {
        QLExpressEngine engine = QLExpress.create();

        Boolean result = engine.execute(
                "contains(name, 'ddd4j') && startsWith(name, 'hello')",
                Collections.singletonMap("name", "hello ddd4j"),
                Boolean.class);

        assertThat(result).isTrue();
    }

    /** \u9a8c\u8bc1<code>engine.validate("if (").valid()</code>\u4e3a\u5047\uff0c<code>engine.validate(" ").message()</code> \u7b49\u4e8e <code>"\u8868\u8fbe\u5f0f\u4e0d\u80fd\u4e3a\u7a7a"</code>\u3002 */
    @Test
    void invalidExpressionShouldReturnValidationFailure() {
        QLExpressEngine engine = QLExpress.create();

        assertThat(engine.validate("if (").valid()).isFalse();
        assertThat(engine.validate(" ").message()).isEqualTo("表达式不能为空");
    }

    /** \u9a8c\u8bc1<code>result.success()</code>\u4e3a\u5047\uff0c<code>result.elapsedNanos()</code>\u4e3a\u6b63\u3002 */
    @Test
    void safeExecutionShouldCaptureFailure() {
        QLExpressEngine engine = QLExpress.create();

        QLExpressExecutionResult<Object> result = engine.executeSafely("missing + 1", Collections.emptyMap());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isNotBlank();
        assertThat(result.elapsedNanos()).isPositive();
    }

    /** \u9a8c\u8bc1<code>engine.execute("multiply(5)", Collections.emptyMap())</code> \u7b49\u4e8e <code>10</code>\uff0c<code>engine.execute("multiply(5)", Collections.emptyMap())</code> \u7b49\u4e8e <code>15</code>\uff0c<code>engine.executeSafely("multiply(5)", Collections.emptyMap()).success()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>engine.getExternalVariables("price * quantity")</code> \u4ec5\u5305\u542b <code>"price"</code>\u3001<code>"quantity"</code>\u3002 */
    @Test
    void expressionAnalysisShouldExposeExternalInputs() {
        QLExpressEngine engine = QLExpress.create();

        assertThat(engine.getExternalVariables("price * quantity"))
                .containsExactlyInAnyOrder("price", "quantity");
    }

    /** \u5b9e\u73b0 <code>NamedQLFunction</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>name()</code>\u3001<code>call()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class MultiplierFunction implements NamedQLFunction {

        /** \u7528\u4e8e\u9a8c\u8bc1\u4e8b\u4ef6\u5904\u7406\u7ed3\u679c\u53d8\u5316\u7684\u500d\u7387\u3002 */
        private final int multiplier;

        /**
         * \u8bbe\u7f6e <code>multiplier</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>MultiplierFunction</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param multiplier \u7528\u4e8e\u521d\u59cb\u5316 <code>MultiplierFunction</code> \u7684 <code>int</code> \u503c
         */
        private MultiplierFunction(int multiplier) {
            this.multiplier = multiplier;
        }

        /**
         * 返回注册或识别当前实现的名称。
         *
         * @return 实际返回<code>"multiply"</code>，供调用方取得当前值
         */
        @Override
        public String name() {
            return "multiply";
        }

        /**
         * \u901a\u8fc7 <code>((Number)parameters.getValue(0)).intValue() * multiplier</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param qContext \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>QContext</code> \u53c2\u6570 <code>qContext</code>
         * @param parameters \u4f20\u7ed9 <code>((Number)parameters.getValue(0)).intValue()</code> \u7684 <code>Parameters</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>((Number)parameters.getValue(0)).intValue() * multiplier</code>
         */
        @Override
        public Object call(QContext qContext, Parameters parameters) {
            return ((Number) parameters.getValue(0)).intValue() * multiplier;
        }
    }
}
