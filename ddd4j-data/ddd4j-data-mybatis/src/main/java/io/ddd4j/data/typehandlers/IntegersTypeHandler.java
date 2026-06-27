package io.ddd4j.data.typehandlers;

import java.util.ArrayList;
import java.util.List;

/**
 * 类型转换：varchar <-> Integer[]，使用英文逗号,分割
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @date 2021/9/12 14:52
 * @since jdk1.8
 */
public class IntegersTypeHandler extends BaseTypeHandler<Integer[]> {

    @Override
    protected String convert(Integer[] obj) {
        if (obj.length == 0) return null;
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