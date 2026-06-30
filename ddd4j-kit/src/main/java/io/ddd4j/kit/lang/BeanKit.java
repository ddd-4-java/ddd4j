package io.ddd4j.kit.lang;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Bean 处理工具类（纯 Java，基于 Hutool，零 Spring 依赖）。
 *
 * <p>统一封装 Bean 拷贝、Bean 与 Map 互转、集合拷贝能力，兼容普通 JavaBean、
 * Lombok {@code @Builder} 以及 {@code @Accessors(chain = true)} 生成的链式访问器。
 *
 * <p>从旧 ddd4j {@code base-core/utils/BeanKit}（Spring {@code BeanUtils} 实现）
 * 重构为基于 Hutool {@link BeanUtil} 的实现，使 {@code ddd4j-data-mybatis} 等数据层
 * 模块不再强依赖 Spring。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class BeanKit {

    /**
     * 将源对象的属性拷贝到目标对象（忽略源对象中为 null 的属性）。
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public void copy(Object source, Object target) {
        if (java.util.Objects.isNull(source) || java.util.Objects.isNull(target)) {
            return;
        }
        // 集合拷贝：逐元素拷贝
        if (source instanceof Collection && target instanceof Collection) {
            Collection<?> src = (Collection<?>) source;
            Collection<Object> tgt = (Collection<Object>) target;
            for (Object s : src) {
                Object t = ReflectUtil.newInstance(s.getClass());
                copy(s, t);
                tgt.add(t);
            }
            return;
        }
        BeanUtil.copyProperties(source, target, CopyOptions.create().ignoreNullValue());
    }

    /**
     * 将源对象拷贝为目标类型的新实例（忽略源对象中为 null 的属性）。
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 目标对象；源对象为 null 时返回 null
     */
    public <T> T copy(Object source, Class<T> targetClass) {
        if (java.util.Objects.isNull(source)) {
            return null;
        }
        T target = ReflectUtil.newInstance(targetClass);
        BeanUtil.copyProperties(source, target, CopyOptions.create().ignoreNullValue());
        return target;
    }

    /**
     * 将源对象拷贝为目标类型的新实例（指定忽略的属性）。
     *
     * @param source           源对象
     * @param targetClass      目标类型
     * @param ignoreProperties 忽略的属性名
     * @param <T>              目标类型泛型
     * @return 目标对象；源对象为 null 时返回 null
     */
    public <T> T copy(Object source, Class<T> targetClass, String... ignoreProperties) {
        if (java.util.Objects.isNull(source)) {
            return null;
        }
        T target = ReflectUtil.newInstance(targetClass);
        BeanUtil.copyProperties(source, target, CopyOptions.create().setIgnoreProperties(ignoreProperties));
        return target;
    }

    /**
     * 批量将源集合拷贝为目标类型的列表。
     *
     * @param sourceList  源集合
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 目标列表；源集合为 null 时返回 null
     */
    public <T> List<T> copy(Collection<?> sourceList, Class<T> targetClass) {
        if (java.util.Objects.isNull(sourceList)) {
            return null;
        }
        List<T> targetList = new ArrayList<>(sourceList.size());
        for (Object source : sourceList) {
            targetList.add(copy(source, targetClass));
        }
        return targetList;
    }

    /**
     * 批量将源集合拷贝为目标类型的列表（指定忽略的属性）。
     *
     * @param sourceList       源集合
     * @param targetClass      目标类型
     * @param ignoreProperties 忽略的属性名
     * @param <T>              目标类型泛型
     * @return 目标列表；源集合为 null 时返回 null
     */
    public <T> List<T> copy(Collection<?> sourceList, Class<T> targetClass, String... ignoreProperties) {
        if (java.util.Objects.isNull(sourceList)) {
            return null;
        }
        List<T> targetList = new ArrayList<>(sourceList.size());
        for (Object source : sourceList) {
            targetList.add(copy(source, targetClass, ignoreProperties));
        }
        return targetList;
    }

    /**
     * 将对象转换为 Map（含 null 值属性）。
     *
     * @param obj 源对象
     * @return 属性 Map；源对象为 null 时返回 null
     */
    public Map<String, Object> toMap(Object obj) {
        return toMap(obj, true, (String[]) null);
    }

    /**
     * 将对象转换为 Map（不含 null 值属性）。
     *
     * @param obj 源对象
     * @return 属性 Map；源对象为 null 时返回 null
     */
    public Map<String, Object> toMapClean(Object obj) {
        return toMap(obj, false, (String[]) null);
    }

    /**
     * 将对象转换为 Map。
     *
     * @param obj          源对象
     * @param withNull     是否包含 null 值属性
     * @param ignoreFields 忽略的属性名
     * @return 属性 Map；源对象为 null 时返回 null
     */
    public Map<String, Object> toMap(Object obj, boolean withNull, String... ignoreFields) {
        if (java.util.Objects.isNull(obj)) {
            return null;
        }
        if (obj instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) obj);
        }
        Set<String> ignores = java.util.Objects.isNull(ignoreFields) ? Set.of() : Set.of(ignoreFields);
        Map<String, Object> map = new LinkedHashMap<>();
        // 遍历继承链上的所有非静态字段
        Class<?> clazz = obj.getClass();
        while (java.util.Objects.nonNull(clazz) && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String fieldName = field.getName();
                if (ignores.contains(fieldName)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    if (java.util.Objects.nonNull(value) || withNull) {
                        map.putIfAbsent(fieldName, value);
                    }
                } catch (IllegalAccessException ignore) {
                    // 跳过不可访问字段
                }
            }
            clazz = clazz.getSuperclass();
        }
        return map;
    }

    /**
     * 将 Map 中的属性写入目标对象。
     *
     * @param map    属性 Map
     * @param target 目标对象
     */
    public void mapToObject(Map<String, Object> map, Object target) {
        if (java.util.Objects.isNull(target) || CollUtil.isEmpty(map)) {
            return;
        }
        BeanUtil.fillBeanWithMap(map, target, CopyOptions.create().setIgnoreError(false));
    }

    /**
     * 将 Map 转换为目标类型对象。
     *
     * @param map         属性 Map
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 目标对象；入参为空时返回 null
     */
    public <T> T ofMap(Map<String, Object> map, Class<T> targetClass) {
        if (CollUtil.isEmpty(map)) {
            return null;
        }
        return BeanUtil.toBean(map, targetClass, CopyOptions.create());
    }

    /**
     * 判断对象是否为空（null、空字符串、空集合、空数组、空 Map）。
     *
     * @param obj 待判断对象
     * @return true 表示为空
     */
    public boolean isEmpty(Object obj) {
        if (java.util.Objects.isNull(obj)) {
            return true;
        }
        if (obj instanceof CharSequence) {
            return ((CharSequence) obj).length() == 0;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }
        return false;
    }

    /**
     * 数据库列名（下划线）转换为 Java 字段名（驼峰）。
     * 如 {@code user_name -> userName}。
     *
     * @param columnName 列名
     * @return 字段名
     */
    public String changeColumnToFieldName(String columnName) {
        if (java.util.Objects.isNull(columnName) || io.ddd4j.kit.lang.StrKit.isEmpty(columnName)) {
            return columnName;
        }
        String[] array = columnName.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            String cn = array[i].toLowerCase();
            if (i == 0) {
                sb.append(cn);
            } else {
                sb.append(cn.substring(0, 1).toUpperCase()).append(cn.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * 将源对象属性拷贝到目标对象（默认忽略创建/更新审计字段）。
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public void objectToObject(Object source, Object target) {
        objectToObject(source, target, false, true, true);
    }

    /**
     * 将源对象属性拷贝到目标对象（忽略源对象中为 null 的属性）。
     *
     * @param source     源对象
     * @param target     目标对象
     * @param ignoreNull 是否忽略源对象中为 null 的属性
     */
    public void objectToObject(Object source, Object target, boolean ignoreNull) {
        objectToObject(source, target, ignoreNull, false, false);
    }

    /**
     * 将源对象属性拷贝到目标对象。
     *
     * @param source         源对象
     * @param target         目标对象
     * @param ignoreNull     是否忽略源对象中为 null 的属性
     * @param withCreateInfo 是否包含创建审计字段（creator/creatorCode/createTime）
     * @param withUpdateInfo 是否包含更新审计字段（updater/updaterCode/updateTime）
     */
    public void objectToObject(Object source, Object target, boolean ignoreNull, boolean withCreateInfo, boolean withUpdateInfo) {
        if (java.util.Objects.isNull(source) || java.util.Objects.isNull(target)) {
            return;
        }
        List<String> ignorePropertiesList = new ArrayList<>();
        if (!withCreateInfo) {
            ignorePropertiesList.add("creator");
            ignorePropertiesList.add("creatorCode");
            ignorePropertiesList.add("createTime");
        }
        if (!withUpdateInfo) {
            ignorePropertiesList.add("updater");
            ignorePropertiesList.add("updaterCode");
            ignorePropertiesList.add("updateTime");
        }
        CopyOptions options = CopyOptions.create();
        if (ignoreNull) {
            options.setIgnoreNullValue(true);
        }
        if (!ignorePropertiesList.isEmpty()) {
            options.setIgnoreProperties(ignorePropertiesList.toArray(new String[0]));
        }
        BeanUtil.copyProperties(source, target, options);
    }

    /**
     * 将源对象属性拷贝到目标对象（按更新场景：忽略创建审计字段）。
     *
     * @param source     源对象
     * @param target     目标对象
     * @param ignoreNull 是否忽略源对象中为 null 的属性
     * @param isUpdate   是否为更新场景（true 时忽略创建字段）
     */
    public void objectToObject(Object source, Object target, boolean ignoreNull, boolean isUpdate) {
        objectToObject(source, target, ignoreNull, !isUpdate, true);
    }

    /**
     * 将源对象拷贝为目标类型的新实例（忽略 null 属性 + 审计字段）。
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @param <Q>         源类型泛型
     * @return 目标对象；源对象为 null 时返回 null
     */
    public <T, Q> T of(Q source, Class<T> targetClass) {
        if (Objects.isNull(source)) {
            return null;
        }
        T target = ReflectUtil.newInstance(targetClass);
        objectToObject(source, target, true, true, true);
        return target;
    }

    /**
     * 批量将源集合拷贝为目标类型的列表（忽略 null 属性 + 审计字段）。
     *
     * @param sourceList  源集合
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @param <Q>         源类型泛型
     * @return 目标列表；源集合为空时返回空列表
     */
    public <T, Q> List<T> ofList(List<Q> sourceList, Class<T> targetClass) {
        if (CollUtil.isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        List<T> targetList = new ArrayList<>(sourceList.size());
        for (Q source : sourceList) {
            targetList.add(of(source, targetClass));
        }
        return targetList;
    }

    /**
     * 批量将 Map 集合转换为目标类型的列表。
     *
     * @param sourceList  Map 集合
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 目标列表；源集合为空时返回空列表
     */
    public <T> List<T> ofMapList(List<Map<String, Object>> sourceList, Class<T> targetClass) {
        if (CollUtil.isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        List<T> targetList = new ArrayList<>(sourceList.size());
        for (Map<String, Object> map : sourceList) {
            targetList.add(ofMap(map, targetClass));
        }
        return targetList;
    }

    /**
     * 批量将源集合拷贝到目标集合（目标元素需预先实例化）。
     *
     * @param sourceList  源集合
     * @param targetList  目标集合
     * @param targetClass 目标类型
     */
    public void objectsToObjects(List sourceList, List targetList, Class targetClass) {
        if (CollUtil.isEmpty(sourceList) || java.util.Objects.isNull(targetList)) {
            return;
        }
        for (Object source : sourceList) {
            Object t = ReflectUtil.newInstance(targetClass);
            objectToObject(source, t, false, true, true);
            targetList.add(t);
        }
    }

    /**
     * 批量将 Map 集合转换到目标集合（目标元素需预先实例化）。
     *
     * @param mapList     Map 集合
     * @param targetList  目标集合
     * @param targetClass 目标类型
     */
    public void mapsToObjects(List<Map<String, Object>> mapList, List targetList, Class targetClass) {
        if (CollUtil.isEmpty(mapList) || java.util.Objects.isNull(targetList)) {
            return;
        }
        for (Map<String, Object> map : mapList) {
            Object target = ReflectUtil.newInstance(targetClass);
            mapToObject(map, target);
            targetList.add(target);
        }
    }

    /**
     * 将字符串列表拼接为单个字符串（默认英文逗号分隔）。
     *
     * @param list 字符串列表
     * @return 拼接结果
     */
    public String listToString(List<String> list) {
        return listToString(list, ",");
    }

    /**
     * 将字符串列表拼接为单个字符串。
     *
     * @param list      字符串列表
     * @param separator 分隔符
     * @return 拼接结果
     */
    public String listToString(List<String> list, String separator) {
        return listToString(list, separator, null);
    }

    /**
     * 将字符串列表拼接为单个字符串（可选包围符）。
     *
     * @param list      字符串列表
     * @param separator 分隔符
     * @param surround  每个元素的包围符（如单引号），为 null 时不包围
     * @return 拼接结果
     */
    public String listToString(List<String> list, String separator, String surround) {
        if (CollUtil.isEmpty(list)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (String str : list) {
            if (i++ > 0) {
                builder.append(separator);
            }
            if (java.util.Objects.nonNull(surround)) {
                builder.append(surround).append(str).append(surround);
            } else {
                builder.append(str);
            }
        }
        return builder.toString();
    }

}
