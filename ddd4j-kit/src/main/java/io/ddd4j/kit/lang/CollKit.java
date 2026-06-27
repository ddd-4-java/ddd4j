package io.ddd4j.kit.lang;

import cn.hutool.core.collection.CollUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 集合工具类
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
@UtilityClass
public class CollKit extends CollUtil {

    public <T> T[] convert(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) {
            return null;
        }
        Class tClass = null;
        for (T t : coll) {
            tClass = t.getClass();
            break;
        }
        return coll.toArray((T[]) Array.newInstance(tClass, 0));
    }

    public <T> List<T> convert(T[] array) {
        if (null == array || array.length == 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    public <T> boolean isNotEmpty(T[] array) {
        return null != array && array.length != 0;
    }

    public <T> boolean isEmpty(T[] array) {
        return null == array || array.length == 0;
    }

}