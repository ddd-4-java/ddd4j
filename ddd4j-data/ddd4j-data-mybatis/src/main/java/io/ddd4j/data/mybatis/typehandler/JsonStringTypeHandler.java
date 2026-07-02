package io.ddd4j.data.mybatis.typehandler;

import io.ddd4j.kit.lang.JsonKit;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;

/**
 * 自定义 POJO 类型转换器父类：json/varchar &lt;-&gt; T。
 *
 * <p>继承该类即可实现任意 POJO（含对象数组）与 JSON 字符串的互转。
 * 注意 T 不能是 {@code List} 集合或 {@code Map} 类型，但可以是数组类型
 * （以实现对对象数组的互转）。
 *
 * <p>与 {@link ListTypeHandler} 的区别：
 * <ul>
 *   <li>{@link ListTypeHandler} 专门处理 {@code List<T>}，子类需提供 {@code TypeReference}</li>
 *   <li>本类处理单个 POJO 或 POJO 数组，自动通过反射推断泛型类型</li>
 * </ul>
 *
 * @param <T> 自定义 POJO（一般以 VO 命名）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class JsonStringTypeHandler<T> extends BaseTypeHandler<T> {
    private Class<?> componentType;
    private Object[] componentArray;

    public JsonStringTypeHandler() {
        Class<T> tClass = type();
        // 判断具体的类型是否为数组
        if (tClass.isArray()) {
            Class<Object[]> arrayClass = (Class<Object[]>) tClass;
            this.componentType = arrayClass.getComponentType();
            this.componentArray = (Object[]) Array.newInstance(componentType, 0);
        }
    }

    @Override
    protected String convert(T obj) {
        // 转换为 Json 字符串
        return JsonKit.toJson(obj);
    }

    @Override
    protected T parse(String json) {
        if (Objects.nonNull(this.componentType)) {
            // Json 解析为对象数组
            List<?> list = JsonKit.toList(json, this.componentType);
            if (Objects.isNull(list)) {
                return null;
            }
            return (T) list.toArray(this.componentArray);
        }
        // Json 解析为对象
        return JsonKit.toObject(json, type());
    }

}
