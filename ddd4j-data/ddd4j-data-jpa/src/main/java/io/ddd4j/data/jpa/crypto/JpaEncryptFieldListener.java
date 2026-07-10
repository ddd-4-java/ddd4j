package io.ddd4j.data.jpa.crypto;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.data.mybatis.crypto.Ddd4jFieldCryptoHandler;
import io.ddd4j.data.mybatis.crypto.EncryptField;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * JPA 实体监听器：对 {@link EncryptField} 标注字段自动加解密。
 *
 * <p>满足等保数据加密要求：SM4 对称加密 + SM3 摘要签名。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Entity
 * @EntityListeners(JpaEncryptFieldListener.class)
 * public class UserEntity {
 *     @EncryptField
 *     private String phone;
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public class JpaEncryptFieldListener {

    private static final Logger log = LoggerFactory.getLogger(JpaEncryptFieldListener.class);

    private final Ddd4jFieldCryptoHandler fieldCryptoHandler;

    public JpaEncryptFieldListener() {
        this.fieldCryptoHandler = null;
    }

    public JpaEncryptFieldListener(CryptoStrategy cryptoStrategy) {
        this.fieldCryptoHandler = new Ddd4jFieldCryptoHandler(cryptoStrategy);
    }

    public JpaEncryptFieldListener(Ddd4jFieldCryptoHandler fieldCryptoHandler) {
        this.fieldCryptoHandler = fieldCryptoHandler;
    }

    @PrePersist
    public void prePersist(Object entity) {
        encryptFields(entity);
    }

    @PostLoad
    public void postLoad(Object entity) {
        decryptFields(entity);
    }

    private void encryptFields(Object entity) {
        if (fieldCryptoHandler == null) return;
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

    private void decryptFields(Object entity) {
        if (fieldCryptoHandler == null) return;
        for (Field field : getAllFields(entity.getClass())) {
            EncryptField annotation = field.getAnnotation(EncryptField.class);
            if (annotation != null) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value instanceof String str && !str.isEmpty()) {
                        String decrypted = fieldCryptoHandler.decrypt(str);
                        field.set(entity, decrypted);
                        log.debug("Decrypted field {}: {} -> {}", field.getName(), str, decrypted);
                    }
                } catch (Exception e) {
                    log.error("Failed to decrypt field {}: {}", field.getName(), e.getMessage());
                }
            }
        }
    }

    private Field[] getAllFields(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return new Field[0];
        Field[] parentFields = getAllFields(clazz.getSuperclass());
        Field[] currentFields = clazz.getDeclaredFields();
        Field[] allFields = new Field[parentFields.length + currentFields.length];
        System.arraycopy(parentFields, 0, allFields, 0, parentFields.length);
        System.arraycopy(currentFields, 0, allFields, parentFields.length, currentFields.length);
        return allFields;
    }
}
