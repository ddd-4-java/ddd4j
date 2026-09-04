package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;

import java.util.Objects;

/**
 * 判断字符串是否包含目标文本。
 */
public final class ContainsFunction implements NamedQLFunction {

    @Override
    public String name() {
        return "contains";
    }

    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object source = FunctionArguments.value(parameters, 0);
        Object target = FunctionArguments.value(parameters, 1);
        return Objects.nonNull(source) && Objects.nonNull(target)
                && source.toString().contains(target.toString());
    }
}
