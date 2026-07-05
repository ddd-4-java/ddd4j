package io.ddd4j.data.typehandlers;

import io.ddd4j.kit.lang.JsonKit;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;

/**
 * 自定义POJO类型转换器父类：json/varchar <-> T
 * 继承该类并加@Component注解，放在entity下的typehandlers目录，PO类无需在@TableField注解上加类型处理器，能自动转换
 *
 * @param <T> 自定义POJO，一般以VO命名，与Model同目录，注意T不能是List集合或Map类型，但可以是数组类型（以实现对对象数组的互转）
 * @author Jensen
 * @公众号 架构师修行录
 * @date 2024/5/6 14:52
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
        // 转换为Json字符串
        return JsonKit.toJson(obj);
    }

    @Override
    protected T parse(String json) {
        if (Objects.nonNull(this.componentType)) {
            // Json解析为对象数组
            List<?> list = JsonKit.toList(json, this.componentType);
            if (Objects.isNull(list)) return null;
            return (T) list.toArray(this.componentArray);
        }
        // Json解析为对象
        return JsonKit.toObject(json, type());
    }

}