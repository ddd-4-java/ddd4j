package io.ddd4j.data.mybatis.typehandler;

/**
 * 类型转换：varchar &lt;-&gt; String[]，使用英文逗号 {@code ,} 分割。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class StringsTypeHandler extends BaseTypeHandler<String[]> {
    @Override
    protected String convert(String[] obj) {
        return String.join(",", obj);
    }

    @Override
    protected String[] parse(String result) {
        return result.split(",");
    }

}
