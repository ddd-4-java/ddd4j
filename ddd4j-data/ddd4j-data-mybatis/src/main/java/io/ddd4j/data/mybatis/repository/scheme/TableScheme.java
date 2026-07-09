package io.ddd4j.data.mybatis.repository.scheme;

import io.ddd4j.annotation.orm.*;
import io.ddd4j.kit.lang.StrKit;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PO 表结构元数据（基于注解反射，构造时一次性解析）。
 *
 * <p>从 PO 类上读取以下信息：
 * <ul>
 *   <li>表名（{@code @TableName} 或类名驼峰转下划线）</li>
 *   <li>主键字段（{@code @TableId} 或名为 {@code id} 的字段）</li>
 *   <li>业务键字段（{@code @BizKey}）</li>
 *   <li>租户隔离字段（{@code @TenantId}）</li>
 *   <li>系统隔离字段（{@code @SystemId}）</li>
 *   <li>自动填充字段（{@code @OnCreate} / {@code @OnUpdate}）</li>
 *   <li>默认排序（{@code @OrderBy}）</li>
 *   <li>字段名 → 列名映射（驼峰转下划线）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class TableScheme implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Pattern CAMEL_CASE = Pattern.compile("([a-z])([A-Z])");
    private static final Map<Class<?>, TableScheme> CACHE = new ConcurrentHashMap<>();

    // ========================= 元数据 =========================

    private final Class<?> poClass;
    private String tableName;
    private Field idField;
    private String idColumn;
    private Field bizKeyField;
    private String bizKeyColumn;
    private Field tenantIdField;
    private String tenantIdColumn;
    private Field systemIdField;
    private String systemIdColumn;
    private List<Field> onCreateFields = new ArrayList<>();
    private List<Field> onUpdateFields = new ArrayList<>();
    private String defaultOrderBy;

    /** fieldName(小写) → columnName */
    private final Map<String, String> field2Column = new LinkedHashMap<>();
    /** columnName → fieldName */
    private final Map<String, String> column2Field = new LinkedHashMap<>();

    // ========================= 构造 =========================

    private TableScheme(Class<?> poClass) {
        this.poClass = poClass;
        build();
    }

    /**
     * 获取 PO 类的 TableScheme（带缓存）。
     */
    public static TableScheme of(Class<?> poClass) {
        return CACHE.computeIfAbsent(poClass, TableScheme::new);
    }

    // ========================= 构建 =========================

    private void build() {
        // 表名：优先 @TableName，否则类名驼峰转下划线
        this.tableName = resolveTableName();

        // 遍历所有字段（含父类）
        List<Field> allFields = getAllFields(poClass);
        for (Field field : allFields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);

            String columnName = resolveColumnName(field);
            field2Column.put(field.getName().toLowerCase(), columnName);
            column2Field.put(columnName, field.getName());

            // 主键
            if (idField == null && isIdField(field)) {
                this.idField = field;
                this.idColumn = columnName;
            }

            // 业务键
            if (field.isAnnotationPresent(BizKey.class)) {
                this.bizKeyField = field;
                this.bizKeyColumn = columnName;
            }

            // 租户隔离
            if (field.isAnnotationPresent(TenantId.class)) {
                this.tenantIdField = field;
                this.tenantIdColumn = columnName;
            }

            // 系统隔离
            if (field.isAnnotationPresent(SystemId.class)) {
                this.systemIdField = field;
                this.systemIdColumn = columnName;
            }

            // 自动填充 - 创建
            if (field.isAnnotationPresent(OnCreate.class)) {
                onCreateFields.add(field);
            }

            // 自动填充 - 更新
            if (field.isAnnotationPresent(OnUpdate.class)) {
                onUpdateFields.add(field);
            }
        }

        // 默认排序：优先类上 @OrderBy，否则用 idField
        OrderBy orderByAnnotation = findAnnotation(poClass, OrderBy.class);
        if (orderByAnnotation != null && orderByAnnotation.value().length > 0
                && !orderByAnnotation.value()[0].isEmpty()) {
            this.defaultOrderBy = String.join(", ", orderByAnnotation.value());
        } else if (idColumn != null) {
            this.defaultOrderBy = idColumn + " DESC";
        }
    }

    // ========================= 表名解析 =========================

    private String resolveTableName() {
        // 优先读取 MyBatis-Plus 的 @TableName（如果存在）
        try {
            Class<?> tableNameClass = Class.forName("com.baomidou.mybatisplus.annotation.TableName");
            java.lang.annotation.Annotation ann = poClass.getAnnotation((Class<java.lang.annotation.Annotation>) tableNameClass);
            if (ann != null) {
                java.lang.reflect.Method valueMethod = tableNameClass.getMethod("value");
                String value = (String) valueMethod.invoke(ann);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // MyBatis-Plus 不在 classpath，走默认逻辑
        }
        // 默认：类名驼峰转下划线（去除 PO/DO/Entity 后缀）
        String simpleName = poClass.getSimpleName();
        simpleName = simpleName.replaceAll("(?i)(PO|DO|Entity)$", "");
        return toUnderline(simpleName);
    }

    // ========================= 列名解析 =========================

    private String resolveColumnName(Field field) {
        // 优先读取 MyBatis-Plus 的 @TableField（如果存在）
        try {
            Class<?> tableFieldClass = Class.forName("com.baomidou.mybatisplus.annotation.TableField");
            java.lang.annotation.Annotation ann = field.getAnnotation((Class<java.lang.annotation.Annotation>) tableFieldClass);
            if (ann != null) {
                java.lang.reflect.Method valueMethod = tableFieldClass.getMethod("value");
                String value = (String) valueMethod.invoke(ann);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        // 优先读取 JPA 的 @Column（如果存在）
        try {
            jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
            if (column != null && !column.name().isEmpty()) {
                return column.name();
            }
        } catch (Exception ignored) {
        }
        // 默认：字段名驼峰转下划线
        return toUnderline(field.getName());
    }

    private boolean isIdField(Field field) {
        // @TableId（MyBatis-Plus）
        try {
            Class<?> tableIdClass = Class.forName("com.baomidou.mybatisplus.annotation.TableId");
            if (field.isAnnotationPresent((Class<java.lang.annotation.Annotation>) tableIdClass)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        // @Id（JPA）
        try {
            if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        // 默认：字段名为 id
        return "id".equals(field.getName());
    }

    // ========================= 自动填充 =========================

    /**
     * INSERT 前自动填充（tenantId / systemId / onCreate / 逻辑删除默认值）。
     */
    public void insertFill(Object po) {
        // 租户 ID
        if (tenantIdField != null) {
            String tenantId = io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.TENANT_ID);
            if (tenantId != null) {
                setFieldValue(po, tenantIdField, tenantId);
            }
        }
        // 系统 ID
        if (systemIdField != null) {
            String systemId = io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.SYSTEM_ID);
            if (systemId != null) {
                setFieldValue(po, systemIdField, systemId);
            }
        }
        // OnCreate 自动填充
        for (Field field : onCreateFields) {
            try {
                Object current = field.get(po);
                if (current == null) {
                    setFieldValue(po, field, now(field.getType()));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    /**
     * UPDATE 前自动填充（onUpdate）。
     */
    public void updateFill(Object po) {
        for (Field field : onUpdateFields) {
            setFieldValue(po, field, now(field.getType()));
        }
    }

    // ========================= 字段/列名查询 =========================

    /**
     * 字段名是否存在于 PO 中（不区分大小写）。
     */
    public boolean containsField(String fieldName) {
        return field2Column.containsKey(fieldName.toLowerCase());
    }

    /**
     * 列名是否存在于 PO 中。
     */
    public boolean containsColumn(String columnName) {
        return column2Field.containsKey(columnName);
    }

    /**
     * 根据字段名获取列名。
     */
    public String getColumn(String fieldName) {
        return field2Column.get(fieldName.toLowerCase());
    }

    /**
     * 根据字段名获取列名（带验证，不存在则返回 null）。
     */
    public String getColumnSafely(String fieldName) {
        String column = field2Column.get(fieldName.toLowerCase());
        if (column == null) {
            // 尝试驼峰转下划线
            column = toUnderline(fieldName);
            if (!column2Field.containsKey(column)) {
                return null;
            }
        }
        return column;
    }

    /**
     * 获取 PO 字段值。
     */
    public Object getFieldValue(Object po, Field field) {
        try {
            field.setAccessible(true);
            return field.get(po);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 获取 PO 字段值（按字段名）。
     */
    public Object getFieldValue(Object po, String fieldName) {
        Field field = findField(po.getClass(), fieldName);
        return field != null ? getFieldValue(po, field) : null;
    }

    // ========================= Getter =========================

    public Class<?> getPoClass() { return poClass; }
    public String getTableName() { return tableName; }
    public Field getIdField() { return idField; }
    public String getIdColumn() { return idColumn; }
    public Field getBizKeyField() { return bizKeyField; }
    public String getBizKeyColumn() { return bizKeyColumn; }
    public Field getTenantIdField() { return tenantIdField; }
    public String getTenantIdColumn() { return tenantIdColumn; }
    public Field getSystemIdField() { return systemIdField; }
    public String getSystemIdColumn() { return systemIdColumn; }
    public List<Field> getOnCreateFields() { return onCreateFields; }
    public List<Field> getOnUpdateFields() { return onUpdateFields; }
    public String getDefaultOrderBy() { return defaultOrderBy; }
    public Map<String, String> getField2Column() { return field2Column; }
    public Map<String, String> getColumn2Field() { return column2Field; }

    public boolean hasBizKey() { return bizKeyField != null; }
    public boolean hasTenantId() { return tenantIdField != null; }
    public boolean hasSystemId() { return systemIdField != null; }

    // ========================= 工具方法 =========================

    private static String toUnderline(String camelCase) {
        Matcher matcher = CAMEL_CASE.matcher(camelCase);
        return matcher.replaceAll("$1_$2").toLowerCase();
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(FieldUtils.getAllFieldsList(current));
            current = current.getSuperclass();
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static <A extends java.lang.annotation.Annotation> A findAnnotation(Class<?> clazz, Class<A> annotationType) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            A ann = current.getAnnotation(annotationType);
            if (ann != null) {
                return ann;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void setFieldValue(Object obj, Field field, Object value) {
        try {
            field.setAccessible(true);
            // 类型兼容：String ← String
            if (field.getType() == String.class && value != null) {
                field.set(obj, value.toString());
            } else {
                field.set(obj, value);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Object now(Class<?> type) {
        if (type == LocalDateTime.class) {
            return LocalDateTime.now();
        }
        if (type == LocalDate.class) {
            return LocalDate.now();
        }
        return null;
    }
}
