package io.ddd4j.data.mybatis.crypto;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * ddd4j 字段加密拦截器（零 Spring 依赖，零 mybatis-enhance-extension 依赖）。
 *
 * <p>对 update 操作进行拦截，对 {@link EncryptField} 标注的字段进行 SM4 加密处理。
 * 桥接 ddd4j-data-crypto 的 {@link CryptoStrategy} 实现透明加解密。
 *
 * <p>满足等保数据加密要求：SM4 对称加密 + SM3 摘要签名。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class Ddd4jCryptoInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(Ddd4jCryptoInterceptor.class);

    private final Ddd4jFieldCryptoHandler fieldCryptoHandler;

    public Ddd4jCryptoInterceptor(CryptoStrategy cryptoStrategy) {
        this.fieldCryptoHandler = new Ddd4jFieldCryptoHandler(cryptoStrategy);
    }

    public Ddd4jCryptoInterceptor(Ddd4jFieldCryptoHandler fieldCryptoHandler) {
        this.fieldCryptoHandler = Objects.requireNonNull(fieldCryptoHandler, "fieldCryptoHandler must not be null");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String methodName = invocation.getMethod().getName();
        if (!StringUtils.equals("update", methodName)) {
            return invocation.proceed();
        }

        Object param = invocation.getArgs()[1];
        if (Objects.isNull(param)) {
            return invocation.proceed();
        }

        // 加密处理
        encryptFields(param);
        return invocation.proceed();
    }

    private void encryptFields(Object entity) {
        for (Field field : getAllFields(entity.getClass())) {
            EncryptField annotation = field.getAnnotation(EncryptField.class);
            if (annotation != null) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value instanceof String str && !str.isEmpty()) {
                        String encrypted = fieldCryptoHandler.encrypt(str);
                        field.set(entity, encrypted);
                        log.debug("Encrypted field {}: {} -> {}", field.getName(), str, encrypted);
                    }
                } catch (Exception e) {
                    log.error("Failed to encrypt field {}: {}", field.getName(), e.getMessage());
                }
            }
        }
    }

    private Field[] getAllFields(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return new Field[0];
        }
        Field[] parentFields = getAllFields(clazz.getSuperclass());
        Field[] currentFields = clazz.getDeclaredFields();
        Field[] allFields = new Field[parentFields.length + currentFields.length];
        System.arraycopy(parentFields, 0, allFields, 0, parentFields.length);
        System.arraycopy(currentFields, 0, allFields, parentFields.length, currentFields.length);
        return allFields;
    }
}
