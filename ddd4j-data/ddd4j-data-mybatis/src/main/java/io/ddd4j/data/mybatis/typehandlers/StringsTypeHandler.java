package io.ddd4j.data.mybatis.typehandlers;

/**
 * 类型转换：varchar <-> String[]，使用英文逗号,分割
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @date 2021/9/12 14:52
 * @since jdk1.8
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