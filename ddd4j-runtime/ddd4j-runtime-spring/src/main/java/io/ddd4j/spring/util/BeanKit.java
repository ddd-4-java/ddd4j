/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.spring.util;

import io.ddd4j.core.exception.BizRuntimeException;
import lombok.experimental.UtilityClass;
import org.springframework.beans.*;

import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;

/**
 * Bean 处理工具类。
 *
 * <p>统一封装 Bean、Map、集合之间的转换能力，兼容普通 JavaBean、Lombok
 * {@code @Builder} 以及 {@code @Accessors(chain = true)} 生成的链式访问器。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class BeanKit {
    /** \u53cd\u5c04\u521b\u5efa\u5bf9\u8c61\u5931\u8d25\u65f6\u4f7f\u7528\u7684\u8bca\u65ad\u6a21\u677f\u3002 */
    private final String REFLECT_ERROR = "反射获取转换对象异常";
    /** \u5206\u9694\u5f85\u5ffd\u7565\u7684 Bean \u5c5e\u6027\u540d\u6240\u7528\u7684\u9017\u53f7\u3002 */
    private final String COMMA = ",";

    /**
     * 将 Map 中的属性写入目标对象。
     * <p>底层基于 Spring {@link BeanWrapper} 完成属性绑定，可兼容链式 setter。</p>
     *
     * @param map    属性 Map
     * @param object 目标对象
     */
    public void mapToObject(Map map, Object object) {
        if (Objects.nonNull(object) && Objects.nonNull(map)) {
            BeanWrapper beanWrapper = new BeanWrapperImpl(object);
            PropertyValues propertyValues = new MutablePropertyValues(map);
            beanWrapper.setPropertyValues(propertyValues, true, true);
        }
    }

    /**
     * \u5c06\u4e0b\u5212\u7ebf\u5217\u540d\u8f6c\u6362\u4e3a\u9a7c\u5cf0\u5b57\u6bb5\u540d\u3002
     * @param columnName \u6570\u636e\u5e93\u5217\u540d
     * @return \u9a7c\u5cf0\u5b57\u6bb5\u540d\uff1b\u8f93\u5165\u6ca1\u6709\u53ef\u7528\u7247\u6bb5\u65f6\u8fd4\u56de {@code null}
     */
    public String changeColumnToFieldName(String columnName) {
        String[] array = columnName.split("_");
        StringBuilder sb = null;
        String[] var3 = array;
        int var4 = array.length;

        for (int var5 = 0; var5 < var4; ++var5) {
            String cn = var3[var5];
            cn = cn.toLowerCase();
            if (Objects.isNull(sb)) {
                sb = new StringBuilder(cn);
            } else {
                sb.append(cn.substring(0, 1).toUpperCase()).append(cn.substring(1));
            }
        }

        return Objects.nonNull(sb) ? sb.toString() : null;
    }

    /**
     * \u5c06\u5bf9\u8c61\u5c5e\u6027\u8f6c\u6362\u4e3a\u5305\u542b\u7a7a\u503c\u7684 Map\u3002
     * @param obj \u5f85\u8f6c\u6362\u6216\u68c0\u67e5\u7684\u5bf9\u8c61
     * @return \u8f6c\u6362\u540e\u7684\u5c5e\u6027 Map\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public Map<String, Object> toMap(Object obj) {
        return toMap(obj, true, new String[0]);
    }

    /**
     * \u5c06\u5bf9\u8c61\u975e\u7a7a\u5c5e\u6027\u8f6c\u6362\u4e3a Map\u3002
     * @param obj \u5f85\u8f6c\u6362\u6216\u68c0\u67e5\u7684\u5bf9\u8c61
     * @return \u8f6c\u6362\u540e\u7684\u5c5e\u6027 Map\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public Map<String, Object> toMapClean(Object obj) {
        return toMap(obj, false, new String[0]);
    }

    /**
     * \u6309\u7a7a\u503c\u548c\u5ffd\u7565\u5b57\u6bb5\u8bbe\u7f6e\u5c06\u5bf9\u8c61\u8f6c\u6362\u4e3a Map\u3002
     * @param obj \u5f85\u8f6c\u6362\u6216\u68c0\u67e5\u7684\u5bf9\u8c61
     * @param withNull \u662f\u5426\u5305\u542b\u7a7a\u503c
     * @param ignoreFields \u8981\u5ffd\u7565\u7684\u5b57\u6bb5\u540d
     * @return \u8f6c\u6362\u540e\u7684\u5c5e\u6027 Map\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public Map<String, Object> toMap(Object obj, boolean withNull, String... ignoreFields) {
        if (Objects.isNull(obj)) {
            return null;
        } else {
            Map<String, Object> map = new HashMap<>();
            if (obj instanceof String) {
                map.put((String) obj, obj);
            } else {
                toMap(obj, map, withNull, ignoreFields);
            }
            return map;
        }
    }

    /**
     * \u5c06\u5c5e\u6027 Map \u5217\u8868\u8f6c\u6362\u5e76\u8ffd\u52a0\u5230\u76ee\u6807\u5bf9\u8c61\u5217\u8868\u3002
     * @param mapList \u5c5e\u6027 Map \u5217\u8868
     * @param objectList \u63a5\u6536\u8f6c\u6362\u7ed3\u679c\u7684\u5217\u8868
     * @param clazz \u5f85\u5b9e\u4f8b\u5316\u7684\u7c7b\u578b
     */
    public void mapsToObjects(List<Map<String, Object>> mapList, List objectList, Class clazz) {
        if (Objects.nonNull(mapList) && !mapList.isEmpty() && Objects.nonNull(objectList)) {
            Iterator var3 = mapList.iterator();

            while (var3.hasNext()) {
                Map map = (Map) var3.next();
                Object object = gainInstanceByReflect(clazz);
                mapToObject(map, object);
                objectList.add(object);
            }
        }

    }

    /**
     * \u5c06\u6e90\u5bf9\u8c61\u5217\u8868\u590d\u5236\u5e76\u8ffd\u52a0\u5230\u76ee\u6807\u5bf9\u8c61\u5217\u8868\u3002
     * @param sourceList \u6e90\u5bf9\u8c61\u96c6\u5408
     * @param targetList \u63a5\u6536\u590d\u5236\u7ed3\u679c\u7684\u5217\u8868
     * @param targetClass \u76ee\u6807\u7c7b\u578b
     */
    public void objectsToObjects(List sourceList, List targetList, Class targetClass) {
        if (Objects.nonNull(sourceList) && !sourceList.isEmpty() && Objects.nonNull(targetList)) {
            Iterator var3 = sourceList.iterator();

            while (var3.hasNext()) {
                Object source = var3.next();
                Object target = gainInstanceByReflect(targetClass);
                BeanUtils.copyProperties(source, target);
                targetList.add(target);
            }
        }

    }

    /**
     * \u4f7f\u7528\u9017\u53f7\u8fde\u63a5\u5b57\u7b26\u4e32\u5217\u8868\u3002
     * @param list \u5f85\u8fde\u63a5\u7684\u5b57\u7b26\u4e32\u5217\u8868
     * @return \u8f6c\u6362\u540e\u7684\u5b57\u7b26\u4e32
     */
    public String listToString(List<String> list) {
        return listToString(list, COMMA);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5206\u9694\u7b26\u8fde\u63a5\u5b57\u7b26\u4e32\u5217\u8868\u3002
     * @param list \u5f85\u8fde\u63a5\u7684\u5b57\u7b26\u4e32\u5217\u8868
     * @param separator \u5143\u7d20\u5206\u9694\u7b26
     * @return \u8f6c\u6362\u540e\u7684\u5b57\u7b26\u4e32
     */
    public String listToString(List<String> list, String separator) {
        return listToString(list, separator, null);
    }

    /**
     * \u4f7f\u7528\u5206\u9694\u7b26\u548c\u5305\u56f4\u5b57\u7b26\u8fde\u63a5\u5b57\u7b26\u4e32\u5217\u8868\u3002
     * @param list \u5f85\u8fde\u63a5\u7684\u5b57\u7b26\u4e32\u5217\u8868
     * @param separator \u5143\u7d20\u5206\u9694\u7b26
     * @param surround \u6bcf\u4e2a\u5143\u7d20\u7684\u5305\u56f4\u5b57\u7b26\u4e32
     * @return \u8f6c\u6362\u540e\u7684\u5b57\u7b26\u4e32
     */
    public String listToString(List<String> list, String separator, String surround) {
        StringBuilder builder = new StringBuilder();
        if (Objects.nonNull(list) && !list.isEmpty()) {
            int i = 0;
            Iterator var5 = list.iterator();

            while (var5.hasNext()) {
                String str = (String) var5.next();
                if (i++ > 0) {
                    builder.append(separator);
                }

                if (Objects.nonNull(surround)) {
                    builder.append(surround).append(str).append(surround);
                } else {
                    builder.append(str);
                }
            }
        }

        return builder.toString();
    }

    /**
     * \u901a\u8fc7\u65e0\u53c2\u6784\u9020\u5668\u521b\u5efa\u6307\u5b9a\u7c7b\u578b\u7684\u5b9e\u4f8b\u3002
     * @param clazz \u5f85\u5b9e\u4f8b\u5316\u7684\u7c7b\u578b
     * @return \u65b0\u521b\u5efa\u7684\u5b9e\u4f8b
     */
    public Object gainInstanceByReflect(Class clazz) {
        String className = clazz.getName();
        return gainInstanceByReflect(className);
    }

    /**
     * \u6839\u636e\u7c7b\u540d\u52a0\u8f7d\u7c7b\u578b\u5e76\u8c03\u7528\u6784\u9020\u5668\u5efa\u7acb\u5bf9\u8c61\u3002
     * @param className \u5f53\u524d\u51c6\u5907\u52a0\u8f7d\u6216\u521b\u5efa\u7684\u7c7b\u540d
     * @return \u901a\u8fc7\u53cd\u5c04\u521b\u5efa\u7684\u5b9e\u4f8b
     */
    private Object gainInstanceByReflect(String className) {
        Object target = null;

        try {
            target = Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BizRuntimeException("{} {}", REFLECT_ERROR, e.getLocalizedMessage());
        }

        return target;
    }

    /**
     * \u5c06\u6e90\u5bf9\u8c61\u5c5e\u6027\u590d\u5236\u5230\u76ee\u6807\u5bf9\u8c61\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     */
    public void objectToObject(Object source, Object target) {
        objectToObject(source, target, false, true, true);
    }

    /**
     * \u6309\u7a7a\u503c\u7b56\u7565\u590d\u5236\u5bf9\u8c61\u5c5e\u6027\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param ignoreNull \u662f\u5426\u8df3\u8fc7\u6e90\u5bf9\u8c61\u7684\u7a7a\u5c5e\u6027
     */
    public void objectToObject(Object source, Object target, boolean ignoreNull) {
        objectToObject(source, target, ignoreNull, false, false);
    }

    /**
     * \u6309\u7a7a\u503c\u53ca\u5ba1\u8ba1\u5b57\u6bb5\u7b56\u7565\u590d\u5236\u5bf9\u8c61\u5c5e\u6027\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param ignoreNull \u662f\u5426\u8df3\u8fc7\u6e90\u5bf9\u8c61\u7684\u7a7a\u5c5e\u6027
     * @param withCreateInfo \u662f\u5426\u590d\u5236\u521b\u5efa\u5ba1\u8ba1\u5b57\u6bb5
     * @param withUpdateInfo \u662f\u5426\u590d\u5236\u66f4\u65b0\u5ba1\u8ba1\u5b57\u6bb5
     */
    public void objectToObject(Object source, Object target, boolean ignoreNull, boolean withCreateInfo, boolean withUpdateInfo) {
        if (Objects.nonNull(source) && Objects.nonNull(target)) {
            List<String> ignorePropertiesList = new ArrayList();
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

            if (ignoreNull) {
                ignoreNull(source, ignorePropertiesList);
            }

            String[] ignorePropertiesArray = new String[ignorePropertiesList.size()];
            BeanUtils.copyProperties(source, target, (String[]) ignorePropertiesList.toArray(ignorePropertiesArray));
        }
    }

    /**
     * \u6536\u96c6\u6e90 Bean \u4e2d\u7f3a\u5c11\u503c\u6216\u914d\u7f6e\u5ffd\u7565\u7684\u5c5e\u6027\u540d\u3002
     * @param source \u9700\u8981\u8bfb\u53d6\u5c5e\u6027\u7684\u6e90 Bean
     * @param ignorePropertiesList \u8c03\u7528\u65b9\u6307\u5b9a\u7684\u4e0d\u53c2\u4e0e\u6620\u5c04\u7684\u5c5e\u6027\u540d
     */
    private void ignoreNull(Object source, List<String> ignorePropertiesList) {
        Field[] fields = source.getClass().getDeclaredFields();

        try {
            Field[] var3 = fields;
            int var4 = fields.length;

            for (int var5 = 0; var5 < var4; ++var5) {
                Field field = var3[var5];
                field.setAccessible(true);
                if (Objects.isNull(field.get(source))) {
                    ignorePropertiesList.add(field.getName());
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new BizRuntimeException("{} {}", REFLECT_ERROR, e.getLocalizedMessage());
        }

    }

    /**
     * \u6309\u66f4\u65b0\u6a21\u5f0f\u548c\u7a7a\u503c\u7b56\u7565\u590d\u5236\u5bf9\u8c61\u5c5e\u6027\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param ignoreNull \u662f\u5426\u8df3\u8fc7\u6e90\u5bf9\u8c61\u7684\u7a7a\u5c5e\u6027
     * @param isUpdate \u662f\u5426\u6309\u66f4\u65b0\u6a21\u5f0f\u590d\u5236
     */
    public void objectToObject(Object source, Object target, boolean ignoreNull, boolean isUpdate) {
        objectToObject(source, target, ignoreNull, !isUpdate, true);
    }

    /**
     * 将 Map 中的属性同步到目标 Bean。
     *
     * <p>仅处理目标对象上存在的可写属性，避免未知字段导致转换流程中断。</p>
     *
     * @param map 属性 Map
     * @param obj 目标对象
     */
    public void transMap2Bean(Map<String, Object> map, Object obj) {
        try {
            PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(obj.getClass());
            PropertyDescriptor[] var4 = propertyDescriptors;
            int var5 = propertyDescriptors.length;

            for (int var6 = 0; var6 < var5; ++var6) {
                PropertyDescriptor property = var4[var6];
                String key = property.getName();
                if (map.containsKey(key)) {
                    Object value = map.get(key);
                    Method setter = property.getWriteMethod();
                    setter.invoke(obj, value);
                }
            }
        } catch (Exception e) {
            throw new BizRuntimeException("Map --> Bean Error {} {} {}", map, obj, e.getLocalizedMessage());
        }

    }

    /**
     * \u5c06\u5bf9\u8c61\u8f6c\u6362\u4e3a\u6307\u5b9a\u7c7b\u578b\uff0c\u5ffd\u7565\u6e90\u5bf9\u8c61\u7684\u7a7a\u5c5e\u6027\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param <Q> \u7c7b\u578b\u53c2\u6570
     * @param q \u5f85\u8f6c\u6362\u7684\u6e90\u5bf9\u8c61
     * @param targetClass \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public <T, Q> T of(Q q, Class<T> targetClass) {
        if (Objects.isNull(q)) {
            return null;
        } else {
            T t = BeanUtils.instantiateClass(targetClass);
            objectToObject(q, t, true, true, true);
            return t;
        }
    }

    /**
     * 将 Map 转换为目标类型对象。
     *
     * @param map         属性 Map
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 转换后的对象；当入参为空时返回 {@code null}
     */
    public <T> T ofMap(Map<String, Object> map, Class<T> targetClass) {
        if (!Objects.isNull(map) && !map.isEmpty()) {
            T t = BeanUtils.instantiateClass(targetClass);
            mapToObject(map, t);
            return t;
        } else {
            return null;
        }
    }

    /**
     * \u5c06\u6e90\u5bf9\u8c61\u5217\u8868\u8f6c\u6362\u4e3a\u76ee\u6807\u7c7b\u578b\u5217\u8868\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param <Q> \u7c7b\u578b\u53c2\u6570
     * @param sources \u5f85\u8f6c\u6362\u7684\u6e90\u5217\u8868
     * @param targetClass \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\u5217\u8868\uff1b\u6e90\u96c6\u5408\u4e3a\u7a7a\u65f6\u9075\u5faa\u5bf9\u5e94\u65b9\u6cd5\u7684\u8fd4\u56de\u7ea6\u5b9a
     */
    public <T, Q> List<T> ofList(List<Q> sources, Class<T> targetClass) {
        if (Objects.isNull(sources) || sources.isEmpty()) {
            return new ArrayList();
        } else {
            List<T> targetList = new ArrayList();
            objectsToObjects(sources, targetList, targetClass);
            return targetList;
        }
    }

    /**
     * \u5c06\u5c5e\u6027 Map \u5217\u8868\u8f6c\u6362\u4e3a\u76ee\u6807\u7c7b\u578b\u5217\u8868\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param sources \u5f85\u8f6c\u6362\u7684\u6e90\u5217\u8868
     * @param targetClass \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\u5217\u8868\uff1b\u6e90\u96c6\u5408\u4e3a\u7a7a\u65f6\u9075\u5faa\u5bf9\u5e94\u65b9\u6cd5\u7684\u8fd4\u56de\u7ea6\u5b9a
     */
    public <T> List<T> ofMapList(List<Map<String, Object>> sources, Class<T> targetClass) {
        if (Objects.isNull(sources) || sources.isEmpty()) {
            return new ArrayList();
        } else {
            List<T> targetList = new ArrayList();
            mapsToObjects(sources, targetList, targetClass);
            return targetList;
        }
    }

    /**
     * \u83b7\u53d6\u6e90\u5bf9\u8c61\u4e2d\u503c\u4e3a null \u7684\u5c5e\u6027\u540d\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @return \u503c\u4e3a\u7a7a\u7684\u5c5e\u6027\u540d\u6570\u7ec4
     */
    public String[] getNullPropertyNames(Object source) {
        BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();
        Set<String> emptyNames = new HashSet();
        PropertyDescriptor[] var4 = pds;
        int var5 = pds.length;

        for (int var6 = 0; var6 < var5; ++var6) {
            PropertyDescriptor pd = var4[var6];
            Object srcValue = src.getPropertyValue(pd.getName());
            if (Objects.isNull(srcValue)) {
                emptyNames.add(pd.getName());
            }
        }

        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }

    /**
     * \u590d\u5236\u5bf9\u8c61\u6216\u96c6\u5408\u4e2d\u7684\u975e\u7a7a\u5c5e\u6027\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     */
    public void copy(Object source, Object target) {
        if (!isEmpty(source) && !isEmpty(target)) {
            if (source instanceof Collection && target instanceof Collection) {
                // 拷贝集合
                Iterator itSource = ((Collection) source).iterator();
                Iterator itTarget = ((Collection) target).iterator();
                while (itSource.hasNext()) {
                    BeanKit.copy(itSource.next(), itTarget.next());
                }
            } else {
                // 拷贝对象
                String[] nullPropertyNames = getNullPropertyNames(source);
                BeanUtils.copyProperties(source, target, nullPropertyNames);
            }
        }
    }

    /**
     * \u590d\u5236\u6e90\u5bf9\u8c61\u5e76\u521b\u5efa\u76ee\u6807\u7c7b\u578b\u5b9e\u4f8b\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public <T> T copy(Object source, Class<T> target) {
        return copy(source, target, (Class) null);
    }

    /**
     * \u590d\u5236\u6e90\u5bf9\u8c61\u5e76\u5728\u6307\u5b9a\u7236\u7c7b\u578b\u8fb9\u754c\u505c\u6b62\u5c5e\u6027\u590d\u5236\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param targetSuper \u5c5e\u6027\u590d\u5236\u505c\u6b62\u7684\u7236\u7c7b\u578b
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public <T> T copy(Object source, Class<T> target, Class<?> targetSuper) {
        if (Objects.isNull(source)) {
            return null;
        } else {
            Object targetObject = null;

            try {
                targetObject = target.getDeclaredConstructor().newInstance();
                BeanUtils.copyProperties(source, targetObject, targetSuper);
            } catch (Exception e) {
                throw new BizRuntimeException("Convert Error {} {} {} {}", source, target, targetSuper, e.getLocalizedMessage());
            }

            return (T) targetObject;
        }
    }

    /**
     * \u9010\u9879\u590d\u5236\u96c6\u5408\u5e76\u521b\u5efa\u76ee\u6807\u7c7b\u578b\u5b9e\u4f8b\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param sourceList \u6e90\u5bf9\u8c61\u96c6\u5408
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\u5217\u8868\uff1b\u6e90\u96c6\u5408\u4e3a\u7a7a\u65f6\u9075\u5faa\u5bf9\u5e94\u65b9\u6cd5\u7684\u8fd4\u56de\u7ea6\u5b9a
     */
    public <T> List<T> copy(Collection<?> sourceList, Class<T> target) {
        if (Objects.isNull(sourceList)) {
            return null;
        } else {
            ArrayList targetList = new ArrayList(sourceList.size());

            try {
                Iterator var3 = sourceList.iterator();

                while (var3.hasNext()) {
                    Object source = var3.next();
                    T targetObject = target.getDeclaredConstructor().newInstance();
                    BeanUtils.copyProperties(source, targetObject);
                    targetList.add(targetObject);
                }
            } catch (Exception e) {
                throw new BizRuntimeException("Map --> Bean Error {} {} {}", sourceList, target, e.getLocalizedMessage());
            }

            return targetList;
        }
    }

    /**
     * \u590d\u5236\u6e90\u5bf9\u8c61\u5e76\u5ffd\u7565\u6307\u5b9a\u5c5e\u6027\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param ignoreProperties \u8981\u5ffd\u7565\u7684\u5c5e\u6027\u540d
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\uff1b\u6e90\u5bf9\u8c61\u4e3a\u7a7a\u65f6\u8fd4\u56de {@code null}
     */
    public <T> T copy(Object source, Class<T> target, String... ignoreProperties) {
        if (Objects.isNull(source)) {
            return null;
        } else {
            Object targetObject = null;

            try {
                targetObject = target.getDeclaredConstructor().newInstance();
                BeanUtils.copyProperties(source, targetObject, ignoreProperties);
            } catch (Exception e) {
                throw new BizRuntimeException("Convert Error {} {} {}", source, target, e.getLocalizedMessage());
            }

            return (T) targetObject;
        }
    }

    /**
     * \u9010\u9879\u590d\u5236\u96c6\u5408\u5e76\u5ffd\u7565\u6307\u5b9a\u5c5e\u6027\u3002
     * @param <T> \u7c7b\u578b\u53c2\u6570
     * @param sourceList \u6e90\u5bf9\u8c61\u96c6\u5408
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param ignoreProperties \u8981\u5ffd\u7565\u7684\u5c5e\u6027\u540d
     * @return \u8f6c\u6362\u6216\u590d\u5236\u540e\u7684\u76ee\u6807\u5bf9\u8c61\u5217\u8868\uff1b\u6e90\u96c6\u5408\u4e3a\u7a7a\u65f6\u9075\u5faa\u5bf9\u5e94\u65b9\u6cd5\u7684\u8fd4\u56de\u7ea6\u5b9a
     */
    public <T> List<T> copy(Collection<?> sourceList, Class<T> target, String... ignoreProperties) {
        if (Objects.isNull(sourceList)) {
            return null;
        } else {
            ArrayList targetList = new ArrayList(sourceList.size());

            try {

                for (Object source : sourceList) {
                    T targetObject = target.getDeclaredConstructor().newInstance();
                    BeanUtils.copyProperties(source, targetObject, ignoreProperties);
                    targetList.add(targetObject);
                }
            } catch (Exception e) {
                throw new BizRuntimeException("Convert Error {} {} {}", sourceList, target, e.getLocalizedMessage());
            }

            return targetList;
        }
    }

    /**
     * \u5c06\u6e90\u5bf9\u8c61\u5c5e\u6027\u5199\u5165\u76ee\u6807 Map\u3002
     * @param source \u6e90\u5bf9\u8c61
     * @param target \u76ee\u6807\u5bf9\u8c61\u6216\u76ee\u6807\u7c7b\u578b
     * @param withNull \u662f\u5426\u5305\u542b\u7a7a\u503c
     * @param ignoredFieldCol \u8981\u5ffd\u7565\u7684\u5b57\u6bb5\u540d
     */
    public void toMap(Object source, Map target, boolean withNull, String... ignoredFieldCol) {
        if (Objects.isNull(source)) {
            throw new BizRuntimeException("source cannot be null");
        }
        if (Objects.isNull(target)) {
            throw new BizRuntimeException("target cannot be null");
        }
        Set<String> ignores = new HashSet<>();
        if (Objects.nonNull(ignoredFieldCol)) {
            ignores.addAll(Arrays.asList(ignoredFieldCol));
        }
        try {
            Set<String> fieldNames = new HashSet<>();
            Class<?> fieldClass = source.getClass();
            while (Objects.nonNull(fieldClass) && fieldClass != Object.class) {
                Field[] declaredFields = fieldClass.getDeclaredFields();
                for (Field field : declaredFields) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        fieldNames.add(field.getName());
                    }
                }
                fieldClass = fieldClass.getSuperclass();
            }

            PropertyDescriptor[] pds = BeanUtils.getPropertyDescriptors(source.getClass());
            for (PropertyDescriptor pd : pds) {
                String fieldName = pd.getName();
                if ("class".equals(fieldName) || ignores.contains(fieldName)) {
                    continue;
                }
                Method getter = pd.getReadMethod();
                Method setter = pd.getWriteMethod();
                if (!fieldNames.contains(fieldName) && Objects.isNull(setter)) {
                    continue;
                }
                if (Objects.nonNull(getter)) {
                    Object value = getter.invoke(source);
                    if (Objects.nonNull(value) || withNull) {
                        target.put(fieldName, value);
                    }
                } else if (withNull) {
                    target.put(fieldName, null);
                }
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new BizRuntimeException("{} {}", REFLECT_ERROR, e.getLocalizedMessage());
        }

    }

    /**
     * \u5224\u65ad\u5bf9\u8c61\u3001\u5bb9\u5668\u6216\u6587\u672c\u662f\u5426\u4e3a\u7a7a\u3002
     * @param obj \u5f85\u8f6c\u6362\u6216\u68c0\u67e5\u7684\u5bf9\u8c61
     * @return \u5bf9\u8c61\u662f\u5426\u4e3a\u7a7a
     */
    protected boolean isEmpty(Object obj) {
        if (Objects.isNull(obj)) {
            return true;
        } else if (obj instanceof Optional) {
            return !((Optional) obj).isPresent();
        } else if (obj instanceof CharSequence) {
            return ((CharSequence) obj).length() == 0;
        } else if (obj.getClass().isArray()) {
            return Array.getLength(obj) == 0;
        } else if (obj instanceof Collection) {
            return ((Collection) obj).isEmpty();
        } else {
            return obj instanceof Map ? ((Map) obj).isEmpty() : false;
        }
    }

}
