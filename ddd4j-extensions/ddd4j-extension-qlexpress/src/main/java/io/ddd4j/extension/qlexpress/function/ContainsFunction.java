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
 * 判断字符串是否包含目标文本。
 */
public final class ContainsFunction implements NamedQLFunction {

    /**
     * 返回注册或识别当前实现的名称。
     *
     * @return 实际返回<code>"contains"</code>，供调用方取得当前值
     */
    @Override
    public String name() {
        return "contains";
    }

    /**
     * 将两个非空参数转换为字符串并检查源字符串是否包含目标子串。
     *
     * @param qContext   表达式当前的函数调用上下文
     * @param parameters 依次包含源值和待匹配文本的实参
     * @return 两个参数均非空且匹配成功时返回 true
     * @throws IllegalArgumentException 传入的实参少于两个
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object source = FunctionArguments.value(parameters, 0);
        Object target = FunctionArguments.value(parameters, 1);
        return Objects.nonNull(source) && Objects.nonNull(target)
                && source.toString().contains(target.toString());
    }
}
