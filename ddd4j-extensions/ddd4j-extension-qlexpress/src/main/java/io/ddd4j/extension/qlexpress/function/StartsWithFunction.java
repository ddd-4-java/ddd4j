package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;

import java.util.Objects;

/**
 * 判断字符串是否以指定文本开头。
 */
public final class StartsWithFunction implements NamedQLFunction {

    @Override
    public String name() {
        return "startsWith";
    }

    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object source = FunctionArguments.value(parameters, 0);
        Object prefix = FunctionArguments.value(parameters, 1);
        return Objects.nonNull(source) && Objects.nonNull(prefix)
                && source.toString().startsWith(prefix.toString());
    }
}
