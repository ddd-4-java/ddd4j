package io.ddd4j.core.ddd.model.metadata;

import io.ddd4j.annotation.orm.DomainField;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;
import org.apache.commons.lang3.reflect.FieldUtils;

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
 *   <li>{@code TableFieldInfo.property} → {@link DomainFieldInfo#getProperty()}</li>
 *   <li>{@code TableFieldInfo.column} → {@link DomainFieldInfo#getPoColumn()}</li>
 * </ul>
 *
 * <p>充血查询翻译链路：
 * <ol>
 *   <li>业务方 Lambda 引用解析出 property（如 {@code "userName"}）</li>
 *   <li>查 {@link #findField(String)} 找对应的 {@link DomainFieldInfo}</li>
 *   <li>取 {@link DomainFieldInfo#getPoColumn()} 作为 SQL 列名</li>
 * </ol>
 *
 * <p><b>零框架依赖</b>：本类不直接依赖 MyBatis-Plus。
 * PO 字段→列名映射通过构造时传入的 {@code poProperty2ColumnProvider}（由基础设施层注入）实现。
 *
 * @param <M> Domain Model 类型
 * @author wandl
 * @since 2.0.x
 */
public class DomainModelInfo<M> {

    @Getter
    private final Class<M> modelType;
    private final List<DomainFieldInfo> fieldList = new ArrayList<>();
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

    private void build(Function<String, String> poProperty2ColumnProvider) {
        Function<String, String> provider = Objects.nonNull(poProperty2ColumnProvider)
                ? poProperty2ColumnProvider
                : p -> null;

        for (Field field : FieldUtils.getAllFieldsList(modelType)) {
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
     * 解析 Domain 字段对应的 PO 数据库列名。
     *
     * <p>优先级：
     * <ol>
     *   <li>@DomainField(column = "...") → 注解值</li>
     *   <li>@DomainField(poField = "...") → 通过 provider 查对应字段的列名</li>
     *   <li>Domain 字段名 = PO 字段名（默认约定）→ 通过 provider 查</li>
     *   <li>fallback：返回 null（调用方做驼峰转下划线）</li>
     * </ol>
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

    public List<DomainFieldInfo> getFieldList() {
        return Collections.unmodifiableList(fieldList);
    }

    /**
     * 通过 Domain 字段名查找 {@link DomainFieldInfo}（精确匹配）。
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
