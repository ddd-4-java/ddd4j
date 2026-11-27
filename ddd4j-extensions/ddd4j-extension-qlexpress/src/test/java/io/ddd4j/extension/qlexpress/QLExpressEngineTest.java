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

/**
 * 验证 <code>QLExpressEngine</code> 在<code>execute</code>、<code>builtInFunctions</code>、<code>invalidExpression</code>、<code>safeExecution</code>等操作中的行为契约。
 */
class QLExpressEngineTest {

    /**
     * 验证<code>result</code> 等于 <code>60</code>。
     */
    @Test
    void executeShouldReturnExpressionValueInsteadOfNativeWrapper() {
        QLExpressEngine engine = QLExpress.create();

        Object result = engine.execute("price * quantity", new java.util.LinkedHashMap<String, Object>() {{
            put("price", 20);
            put("quantity", 3);
        }});

        assertThat(result).isEqualTo(60);
    }

    /**
     * 验证<code>result</code>为真。
     */
    @Test
    void builtInFunctionsShouldBeAvailableByDefault() {
        QLExpressEngine engine = QLExpress.create();

        Boolean result = engine.execute(
                "contains(name, 'ddd4j') && startsWith(name, 'hello')",
                Collections.singletonMap("name", "hello ddd4j"),
                Boolean.class);

        assertThat(result).isTrue();
    }

    /**
     * 验证<code>engine.validate("if (").valid()</code>为假，<code>engine.validate(" ").message()</code> 等于 <code>"表达式不能为空"</code>。
     */
    @Test
    void invalidExpressionShouldReturnValidationFailure() {
        QLExpressEngine engine = QLExpress.create();

        assertThat(engine.validate("if (").valid()).isFalse();
        assertThat(engine.validate(" ").message()).isEqualTo("表达式不能为空");
    }

    /**
     * 验证<code>result.success()</code>为假，<code>result.elapsedNanos()</code>为正。
     */
    @Test
    void safeExecutionShouldCaptureFailure() {
        QLExpressEngine engine = QLExpress.create();

        QLExpressExecutionResult<Object> result = engine.executeSafely("missing + 1", Collections.emptyMap());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isNotBlank();
        assertThat(result.elapsedNanos()).isPositive();
    }

    /**
     * 验证<code>engine.execute("multiply(5)", Collections.emptyMap())</code> 等于 <code>10</code>，<code>engine.execute("multiply(5)", Collections.emptyMap())</code> 等于 <code>15</code>，<code>engine.executeSafely("multiply(5)", Collections.emptyMap()).success()</code>为假。
     */
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

    /**
     * 验证<code>engine.getExternalVariables("price * quantity")</code> 仅包含 <code>"price"</code>、<code>"quantity"</code>。
     */
    @Test
    void expressionAnalysisShouldExposeExternalInputs() {
        QLExpressEngine engine = QLExpress.create();

        assertThat(engine.getExternalVariables("price * quantity"))
                .containsExactlyInAnyOrder("price", "quantity");
    }

    /**
     * 实现 <code>NamedQLFunction</code>，供测试调用 <code>name()</code>、<code>call()</code> 并观察结果。
     */
    private static final class MultiplierFunction implements NamedQLFunction {

        /**
         * 用于验证事件处理结果变化的倍率。
         */
        private final int multiplier;

        /**
         * 设置 <code>multiplier</code>，用于构造 <code>MultiplierFunction</code> 测试实例。
         *
         * @param multiplier 用于初始化 <code>MultiplierFunction</code> 的 <code>int</code> 值
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
         * 通过 <code>((Number)parameters.getValue(0)).intValue() * multiplier</code> 取得当前测试需要的结果。
         *
         * @param qContext   本次调用使用的 <code>QContext</code> 参数 <code>qContext</code>
         * @param parameters 传给 <code>((Number)parameters.getValue(0)).intValue()</code> 的 <code>Parameters</code> 输入
         * @return 当前测试使用的结果，取自 <code>((Number)parameters.getValue(0)).intValue() * multiplier</code>
         */
        @Override
        public Object call(QContext qContext, Parameters parameters) {
            return ((Number) parameters.getValue(0)).intValue() * multiplier;
        }
    }
}
