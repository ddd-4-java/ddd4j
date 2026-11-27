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
package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;

import java.util.Objects;

/**
 * 判断字符串是否以指定文本结尾。
 */
public final class EndsWithFunction implements NamedQLFunction {

    /**
     * name() 方法。
     * @return 结果字符串。
     */
    @Override
    public String name() {
        return "endsWith";
    }

    /**
     * call() \u65b9\u6cd5\u3002
     * @param qContext qContext \u53c2\u6570\u3002
     * @param parameters parameters \u53c2\u6570\u3002
     * @return \u7ed3\u679c\u5bf9\u8c61\u3002
     *
     * @throws IllegalArgumentException \u4f20\u5165\u7684\u5b9e\u53c2\u5c11\u4e8e\u4e24\u4e2a
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object source = FunctionArguments.value(parameters, 0);
        Object suffix = FunctionArguments.value(parameters, 1);
        return Objects.nonNull(source) && Objects.nonNull(suffix)
                && source.toString().endsWith(suffix.toString());
    }
}
