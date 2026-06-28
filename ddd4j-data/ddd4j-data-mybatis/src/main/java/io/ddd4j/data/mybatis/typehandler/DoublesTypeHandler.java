package io.ddd4j.data.mybatis.typehandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 类型转换：varchar &lt;-&gt; Double[]，使用英文逗号 {@code ,} 分割。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DoublesTypeHandler extends BaseTypeHandler<Double[]> {
    @Override
    protected String convert(Double[] obj) {
        if (obj.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (Double d : obj) {
            sb.append(d).append(",");
        }
        sb.append("<END>");
        return sb.toString().replace(",<END>", "");
    }

    @Override
    protected Double[] parse(String result) {
        String[] split = result.split(",");
        List<Double> doubles = new ArrayList<>();
        for (String s : split) {
            doubles.add(Double.valueOf(s));
        }
        return doubles.toArray(new Double[]{});
    }

}
