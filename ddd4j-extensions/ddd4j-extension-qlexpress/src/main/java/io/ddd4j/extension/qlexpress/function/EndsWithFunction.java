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

    /** {@inheritDoc} */
    @Override
    public String name() {
        return "endsWith";
    }

    /**
     * \u5c06\u4e24\u4e2a\u975e\u7a7a\u53c2\u6570\u8f6c\u6362\u4e3a\u5b57\u7b26\u4e32\u5e76\u68c0\u67e5\u6e90\u5b57\u7b26\u4e32\u662f\u5426\u4ee5\u6307\u5b9a\u540e\u7f00\u7ed3\u5c3e\u3002
     * @param qContext \u8868\u8fbe\u5f0f\u5f53\u524d\u7684\u51fd\u6570\u8c03\u7528\u4e0a\u4e0b\u6587
     * @param parameters \u4f9d\u6b21\u5305\u542b\u6e90\u503c\u548c\u5f85\u5339\u914d\u6587\u672c\u7684\u5b9e\u53c2
     * @return \u4e24\u4e2a\u53c2\u6570\u5747\u975e\u7a7a\u4e14\u5339\u914d\u6210\u529f\u65f6\u8fd4\u56de true
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
