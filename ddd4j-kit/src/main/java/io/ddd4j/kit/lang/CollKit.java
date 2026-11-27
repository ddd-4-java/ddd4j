package io.ddd4j.kit.lang;

import cn.hutool.core.collection.CollUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.util.*;

/**
 * 集合工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class CollKit extends CollUtil {

    /**
     * \u5c06\u96c6\u5408\u8f6c\u6362\u4e3a\u6570\u7ec4\u3002
     * @param coll \u6e90\u96c6\u5408
     * @param <T> \u5143\u7d20\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u6570\u7ec4\uff1b\u96c6\u5408\u4e3a\u7a7a\u65f6\u8fd4\u56de null
     */
    public <T> T[] convert(Collection<T> coll) {
        if (Objects.isNull(coll) || coll.isEmpty()) {
            return null;
        }
        Class tClass = null;
        for (T t : coll) {
            tClass = t.getClass();
            break;
        }
        return coll.toArray((T[]) Array.newInstance(tClass, 0));
    }

    /**
     * \u5c06\u6570\u7ec4\u8f6c\u6362\u4e3a\u5217\u8868\u3002
     * @param array \u6e90\u6570\u7ec4
     * @param <T> \u5143\u7d20\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u5217\u8868\uff1b\u6570\u7ec4\u4e3a\u7a7a\u65f6\u8fd4\u56de\u7a7a\u5217\u8868
     */
    public <T> List<T> convert(T[] array) {
        if (Objects.isNull(array) || array.length == 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * \u5224\u65ad\u6570\u7ec4\u662f\u5426\u975e\u7a7a\u3002
     * @param array \u5f85\u68c0\u67e5\u6570\u7ec4
     * @param <T> \u5143\u7d20\u7c7b\u578b
     * @return \u6570\u7ec4\u975e\u7a7a\u4e14\u5305\u542b\u5143\u7d20\u65f6\u8fd4\u56de true
     */
    public <T> boolean isNotEmpty(T[] array) {
        return Objects.nonNull(array) && array.length != 0;
    }

    /**
     * \u5224\u65ad\u6570\u7ec4\u662f\u5426\u4e3a\u7a7a\u3002
     * @param array \u5f85\u68c0\u67e5\u6570\u7ec4
     * @param <T> \u5143\u7d20\u7c7b\u578b
     * @return \u6570\u7ec4\u4e3a null \u6216\u957f\u5ea6\u4e3a\u96f6\u65f6\u8fd4\u56de true
     */
    public <T> boolean isEmpty(T[] array) {
        return Objects.isNull(array) || array.length == 0;
    }

}