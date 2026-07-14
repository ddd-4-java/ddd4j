package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;

import java.util.Objects;

/**
 * 判断字符串是否以指定文本结尾。
 */
public final class EndsWithFunction implements NamedQLFunction {

    @Override
    public String name() {
        return "endsWith";
    }

    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object source = FunctionArguments.value(parameters, 0);
        Object suffix = FunctionArguments.value(parameters, 1);
        return Objects.nonNull(source) && Objects.nonNull(suffix)
                && source.toString().endsWith(suffix.toString());
    }
}
