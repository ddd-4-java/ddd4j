package io.ddd4j.data.mybatis.typehandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 类型转换：varchar &lt;-&gt; Integer[]，使用英文逗号 {@code ,} 分割。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class IntegersTypeHandler extends BaseTypeHandler<Integer[]> {

    @Override
    protected String convert(Integer[] obj) {
        if (obj.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Integer l : obj) {
            sb.append(l).append(",");
        }
        sb.append("<END>");
        return sb.toString().replace(",<END>", "");
    }

    @Override
    protected Integer[] parse(String result) {
        String[] split = result.split(",");
        List<Integer> integers = new ArrayList<>();
        for (String s : split) {
            integers.add(Integer.valueOf(s));
        }
        return integers.toArray(new Integer[]{});
    }

}
