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
package io.ddd4j.core.ddd.model.metadata;

import io.ddd4j.annotation.orm.DomainField;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;

/**
 * Domain Model 元数据（仿 MyBatis-Plus {@code TableInfo}，充血查询字段映射的索引）。
 *
 * <p>对应 MP {@code TableInfo} 的对偶结构：
 * <ul>
 *   <li>{@code TableInfo.entityType} → {@link #modelType}</li>
 *   <li>{@code TableInfo.fieldList} → {@link #fieldList}</li>
 *   <li>{@code TableFieldInfo.property} → {@code DomainFieldInfo#getProperty()}</li>
 *   <li>{@code TableFieldInfo.column} → {@code DomainFieldInfo#getPoColumn()}</li>
 * </ul>
 *
 * <p>充血查询翻译链路：
 * <ol>
 *   <li>业务方 Lambda 引用解析出 property（如 {@code "userName"}）</li>
 *   <li>查 {@link #findField(String)} 找对应的 {@link DomainFieldInfo}</li>
 *   <li>取 {@link DomainFieldInfo} 的 {@code getPoColumn()} 作为 SQL 列名</li>
 * </ol>
 *
 * <p><b>零框架依赖</b>：本类不直接依赖 MyBatis-Plus。
 * PO 字段→列名映射通过构造时传入的 {@code poProperty2ColumnProvider}（由基础设施层注入）实现。
 *
 * @param <M> Domain Model 类型
 * @author ddd4j
 * @since 2.0.x
 */
public class DomainModelInfo<M> {

    /** \u6b63\u5728\u5206\u6790\u5b57\u6bb5\u5143\u6570\u636e\u7684\u9886\u57df\u6a21\u578b\u7c7b\u578b\u3002 */
    @Getter
    private final Class<M> modelType;
    /** \u5f53\u524d\u9886\u57df\u6a21\u578b\u53c2\u4e0e\u6620\u5c04\u7684\u5b57\u6bb5\u5143\u6570\u636e\u3002 */
    private final List<DomainFieldInfo> fieldList = new ArrayList<>();
    /** \u6309\u6a21\u578b\u5c5e\u6027\u540d\u67e5\u627e\u5b57\u6bb5\u63cf\u8ff0\u7684\u7d22\u5f15\u3002 */
    private final Map<String, DomainFieldInfo> property2Field = new LinkedHashMap<>();

    /**
     * 构造 DomainModelInfo（PO 字段→列名通过 provider 提供）。
     *
     * @param modelType                 Domain Model 类型
     * @param poProperty2ColumnProvider PO property → DB column 映射的 provider（如 MP TableInfo 的 property→column 索引）
     */
    public DomainModelInfo(Class<M> modelType, Function<String, String> poProperty2ColumnProvider) {
        this.modelType = Objects.requireNonNull(modelType, "modelType must not be null");
        build(poProperty2ColumnProvider);
    }

    /**
     * 获取指定类及其所有父类声明的全部字段（等价于 commons-lang3 {@code FieldUtils.getAllFieldsList}）。
     *
     * @param clazz 目标类
     * @return 全部字段列表（包含父类私有字段）
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (Objects.nonNull(current) && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * \u626b\u63cf\u5f53\u524d\u6a21\u578b\u5b57\u6bb5\u5e76\u5efa\u7acb\u5c5e\u6027\u4e0e\u6301\u4e45\u5316\u5217\u7684\u6620\u5c04\u3002
     * @param poProperty2ColumnProvider \u53ef\u9009\u7684\u6301\u4e45\u5316\u5c5e\u6027\u5217\u540d\u89e3\u6790\u51fd\u6570
     */
    private void build(Function<String, String> poProperty2ColumnProvider) {
        Function<String, String> provider = Objects.nonNull(poProperty2ColumnProvider)
                ? poProperty2ColumnProvider
                : p -> null;

        for (Field field : getAllFields(modelType)) {
            if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())
                    || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            String poColumn = resolvePoColumn(field, provider);
            DomainFieldInfo info = new DomainFieldInfo(field, poColumn);
            fieldList.add(info);
            property2Field.put(field.getName(), info);
        }
    }

    /**
     * \u89e3\u6790 Domain \u5b57\u6bb5\u5bf9\u5e94\u7684 PO \u6570\u636e\u5e93\u5217\u540d\u3002
     *
     *  <p>\u4f18\u5148\u7ea7\uff1a
     *  <ol>
     *    <li>@DomainField(column = "...") \u2192 \u6ce8\u89e3\u503c</li>
     *    <li>@DomainField(poField = "...") \u2192 \u901a\u8fc7 provider \u67e5\u5bf9\u5e94\u5b57\u6bb5\u7684\u5217\u540d</li>
     *    <li>Domain \u5b57\u6bb5\u540d = PO \u5b57\u6bb5\u540d\uff08\u9ed8\u8ba4\u7ea6\u5b9a\uff09\u2192 \u901a\u8fc7 provider \u67e5</li>
     *    <li>fallback\uff1a\u8fd4\u56de null\uff08\u8c03\u7528\u65b9\u505a\u9a7c\u5cf0\u8f6c\u4e0b\u5212\u7ebf\uff09</li>
     *  </ol>
     *
     * @param field \u8981\u89e3\u6790\u7684\u5bf9\u8c61\u5b57\u6bb5
     * @param provider \u63d0\u4f9b\u5b57\u6bb5\u5230\u5217\u6620\u5c04\u7684\u5143\u6570\u636e\u7ec4\u4ef6
     * @return \u89e3\u6790\u5f97\u5230\u7684\u6301\u4e45\u5316\u5217\u540d
     */
    private String resolvePoColumn(Field field, Function<String, String> provider) {
        DomainField annotation = field.getAnnotation(DomainField.class);
        if (Objects.nonNull(annotation)) {
            // 优先级 1：直接声明 column
            if (StrKit.hasText(annotation.column())) {
                return annotation.column();
            }
            // 优先级 2：通过 poField 查找
            if (StrKit.hasText(annotation.poField())) {
                String col = provider.apply(annotation.poField());
                if (Objects.nonNull(col)) {
                    return col;
                }
            }
        }
        // 优先级 3：默认约定（Domain 字段名 = PO 字段名）
        return provider.apply(field.getName());
    }

    /**
     * \u83b7\u53d6\u9886\u57df\u6a21\u578b\u5b57\u6bb5\u5143\u6570\u636e\u7684\u4e0d\u53ef\u53d8\u5217\u8868\u3002
     * @return \u5b57\u6bb5\u5143\u6570\u636e\u5217\u8868
     */
    public List<DomainFieldInfo> getFieldList() {
        return Collections.unmodifiableList(fieldList);
    }

    /**
     * 通过 Domain 字段名查找 {@link DomainFieldInfo}（精确匹配）。
     * @param property 属性
     * @return 查找的DomainFieldInfo
     */
    public DomainFieldInfo findField(String property) {
        if (Objects.isNull(property)) {
            return null;
        }
        return property2Field.get(property);
    }

    /**
     * 通过 Domain 字段名获取对应的 PO 数据库列名。
     *
     * @param property Domain 字段名（来自 Lambda 引用解析）
     * @return PO 数据库列名，未匹配返回 null
     */
    public String getPoColumn(String property) {
        DomainFieldInfo info = findField(property);
        return Objects.nonNull(info) ? info.getPoColumn() : null;
    }
}
