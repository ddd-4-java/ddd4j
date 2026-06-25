package io.ddd4j.extension.jackson.ser;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 此modifier主要做的事情为：
 * 1.当序列化类型为数组集合时，当值为null时，序列化成[]
 * 2.String类型值序列化为""
 * </pre>
 */
public class MyBeanSerializerModifier extends BeanSerializerModifier {

    private final boolean defaultNullArraySerializer;
    private final boolean defaultNullNumberSerializer;
    private final boolean defaultNullStringSerializer;
    private final boolean defaultNullDateSerializer;
    private final boolean defaultNullBooleanSerializer;
    private final boolean defaultNullJsonObjectSerializer;

    public MyBeanSerializerModifier() {
        this(true, false, true, true, false, true);
    }

    public MyBeanSerializerModifier(boolean defaultNullArraySerializer,
                                    boolean defaultNullNumberSerializer,
                                    boolean defaultNullStringSerializer,
                                    boolean defaultNullDateSerializer,
                                    boolean defaultNullBooleanSerializer,
                                    boolean defaultNullJsonObjectSerializer) {
        this.defaultNullArraySerializer = defaultNullArraySerializer;
        this.defaultNullNumberSerializer = defaultNullNumberSerializer;
        this.defaultNullStringSerializer = defaultNullStringSerializer;
        this.defaultNullDateSerializer = defaultNullDateSerializer;
        this.defaultNullBooleanSerializer = defaultNullBooleanSerializer;
        this.defaultNullJsonObjectSerializer = defaultNullJsonObjectSerializer;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        // 1、循环所有的beanPropertyWriter
        for (BeanPropertyWriter writer : beanProperties) {
            // 2、当前属性的Java类型
            Class<?> rawClass = writer.getType().getRawClass();
            // 3、判断字段的类型，如果是array，list，set则注册nullSerializer
            if (defaultNullArraySerializer && this.isArrayType(rawClass)) {
                writer.assignNullSerializer(NullArrayJsonSerializer.INSTANCE);
            } else if (defaultNullNumberSerializer && this.isNumberType(rawClass)) {
                writer.assignNullSerializer(NullNumberJsonSerializer.INSTANCE);
            } else if (defaultNullBooleanSerializer && this.isBooleanType(rawClass)) {
                writer.assignNullSerializer(NullBooleanJsonSerializer.INSTANCE);
            } else if (defaultNullStringSerializer && this.isStringType(rawClass)) {
                writer.assignNullSerializer(NullStringJsonSerializer.INSTANCE);
            } else if (defaultNullDateSerializer && this.isDateType(rawClass)) {
                writer.assignNullSerializer(NullDateJsonSerializer.INSTANCE);
            } else if (defaultNullJsonObjectSerializer && this.isJsonObjectType(rawClass)) {
                writer.assignNullSerializer(NullObjectJsonSerializer.INSTANCE);
            }
        }
        return beanProperties;
    }

    /**
     * 1、是否是集合
     */
    protected boolean isArrayType(Class<?> rawClass) {
        return rawClass.isArray() || Collection.class.isAssignableFrom(rawClass);
    }

    /**
     * 2、是否是String
     */
    protected boolean isStringType(Class<?> rawClass) {
        return CharSequence.class.isAssignableFrom(rawClass) || Character.class.isAssignableFrom(rawClass);
    }

    /**
     * 3、是否是Date
     */
    protected boolean isDateType(Class<?> rawClass) {
        return Date.class.isAssignableFrom(rawClass) || java.sql.Date.class.isAssignableFrom(rawClass)
                || LocalDate.class.isAssignableFrom(rawClass)
                || LocalDateTime.class.isAssignableFrom(rawClass)
                || LocalTime.class.isAssignableFrom(rawClass);
    }

    /**
     * 4、是否是数值类型
     */
    protected boolean isNumberType(Class<?> rawClass) {
        return Number.class.isAssignableFrom(rawClass);
    }

    /**
     * 5、是否是boolean
     */
    protected boolean isBooleanType(Class<?> rawClass) {
        return rawClass.equals(Boolean.class);
    }

    /**
     * 6、是否是 json object , Map, POJO
     */
    private boolean isJsonObjectType(Class<?> rawClass) {
        return Map.class.isAssignableFrom(rawClass) || BeanUtil.isBean(rawClass);
    }

}