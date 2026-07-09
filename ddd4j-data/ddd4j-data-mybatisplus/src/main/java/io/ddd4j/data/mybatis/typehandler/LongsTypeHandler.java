package io.ddd4j.data.mybatis.typehandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 类型转换：varchar &lt;-&gt; Long[]，使用英文逗号 {@code ,} 分割。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class LongsTypeHandler extends BaseTypeHandler<Long[]> {
    @Override
    protected String convert(Long[] obj) {
        if (obj.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long l : obj) {
            sb.append(l).append(",");
        }
        sb.append("<END>");
        return sb.toString().replace(",<END>", "");
    }

    @Override
    protected Long[] parse(String result) {
        String[] split = result.split(",");
        List<Long> longs = new ArrayList<>();
        for (String s : split) {
            longs.add(Long.valueOf(s));
        }
        return longs.toArray(new Long[]{});
    }

}
