package io.ddd4j.extension.qlexpress.function;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;

import java.util.Objects;

/**
 * 判断字符串是否以指定文本开头。
 */
public final class StartsWithFunction implements NamedQLFunction {

    /**
     * 返回注册或识别当前实现的名称。
     *
     * @return 实际返回<code>"startsWith"</code>，供调用方取得当前值
     */
    @Override
    public String name() {
        return "startsWith";
    }

    /**
     * \u5c06\u4e24\u4e2a\u975e\u7a7a\u53c2\u6570\u8f6c\u6362\u4e3a\u5b57\u7b26\u4e32\u5e76\u68c0\u67e5\u6e90\u5b57\u7b26\u4e32\u662f\u5426\u4ee5\u6307\u5b9a\u524d\u7f00\u5f00\u5934\u3002
     * @param qContext \u8868\u8fbe\u5f0f\u5f53\u524d\u7684\u51fd\u6570\u8c03\u7528\u4e0a\u4e0b\u6587
     * @param parameters \u4f9d\u6b21\u5305\u542b\u6e90\u503c\u548c\u5f85\u5339\u914d\u6587\u672c\u7684\u5b9e\u53c2
     * @return \u4e24\u4e2a\u53c2\u6570\u5747\u975e\u7a7a\u4e14\u5339\u914d\u6210\u529f\u65f6\u8fd4\u56de true
     * @throws IllegalArgumentException \u4f20\u5165\u7684\u5b9e\u53c2\u5c11\u4e8e\u4e24\u4e2a
     */
    @Override
    public Object call(QContext qContext, Parameters parameters) {
        FunctionArguments.requireSize(parameters, 2, name());
        Object source = FunctionArguments.value(parameters, 0);
        Object prefix = FunctionArguments.value(parameters, 1);
        return Objects.nonNull(source) && Objects.nonNull(prefix)
                && source.toString().startsWith(prefix.toString());
    }
}
