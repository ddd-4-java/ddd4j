package io.ddd4j.data.jpa.crypto;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import io.ddd4j.data.crypto.annotation.EncryptField;
import io.ddd4j.data.crypto.handler.Ddd4jFieldCryptoHandler;
import io.ddd4j.kit.lang.StrKit;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * JPA 实体监听器：对 {@link EncryptField} 标注字段自动加解密。
 *
 * <p>满足等保数据加密要求：SM4 对称加密 + SM3 摘要签名。</p>
 *
 * <h3>加解密 Handler 的获取</h3>
 * <p>JPA 规范要求 {@code @EntityListeners} 监听器由 ORM 以<b>无参构造</b>实例化，
 * 无法通过构造器注入 {@link CryptoStrategy}。本监听器在无参构造时通过
 * {@link ServiceLoader} 延迟发现 {@link CryptoStrategy} 实现；若发现多个则取第一个，
 * 若未发现则加解密静默跳过（与无加密场景兼容）。</p>
 *
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
@Slf4j
public class JpaEncryptFieldListener {

    /**
     * 延迟初始化的加解密 Handler（ServiceLoader 发现 CryptoStrategy 后构建）。
     * volatile 保证多线程可见性。
     */
    private static volatile Ddd4jFieldCryptoHandler cachedHandler;

    private final Ddd4jFieldCryptoHandler fieldCryptoHandler;

    public JpaEncryptFieldListener() {
        this.fieldCryptoHandler = resolveHandler();
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

    @PreUpdate
    public void preUpdate(Object entity) {
        encryptFields(entity);
    }

    @PostLoad
    public void postLoad(Object entity) {
        decryptFields(entity);
    }

    private void encryptFields(Object entity) {
        if (Objects.isNull(fieldCryptoHandler)) {
            return;
        }
        for (Field field : getEncryptFields(entity.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(entity);
                if (value instanceof String str && StrKit.isNotEmpty(str)) {
                    String encrypted = fieldCryptoHandler.encrypt(str);
                    field.set(entity, encrypted);
                    log.debug("Encrypted field {}: {} -> {}", field.getName(), str, encrypted);
                }
            } catch (Exception e) {
                log.error("Failed to encrypt field {}: {}", field.getName(), e.getMessage());
            }
        }
    }

    private void decryptFields(Object entity) {
        if (Objects.isNull(fieldCryptoHandler)) {
            return;
        }
        for (Field field : getEncryptFields(entity.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(entity);
                if (value instanceof String str && StrKit.isNotEmpty(str)) {
                    String decrypted = fieldCryptoHandler.decrypt(str);
                    field.set(entity, decrypted);
                    log.debug("Decrypted field {}: {} -> {}", field.getName(), str, decrypted);
                }
            } catch (Exception e) {
                log.error("Failed to decrypt field {}: {}", field.getName(), e.getMessage());
            }
        }
    }

    /**
     * 获取类（含父类）中所有标注 {@link EncryptField} 的字段。
     *
     * <p>使用 {@link FieldUtils#getAllFieldsList(Class)} 复用 commons-lang3 反射工具，
     * 替代旧版手写递归。</p>
     *
     * @param clazz 实体类
     * @return 标注了 {@code @EncryptField} 的字段列表
     */
    private List<Field> getEncryptFields(Class<?> clazz) {
        return FieldUtils.getAllFieldsList(clazz).stream()
                .filter(field -> Objects.nonNull(field.getAnnotation(EncryptField.class)))
                .toList();
    }

    /**
     * 通过 {@link ServiceLoader} 延迟发现 {@link CryptoStrategy} 实现。
     *
     * <p>首次调用时扫描 classpath，结果缓存在 {@link #cachedHandler}。
     * 未发现任何实现时返回 {@code null}（加解密静默跳过）。</p>
     *
     * @return 加解密 Handler；未配置 {@link CryptoStrategy} 时返回 {@code null}
     */
    private static Ddd4jFieldCryptoHandler resolveHandler() {
        if (Objects.isNull(cachedHandler)) {
            synchronized (JpaEncryptFieldListener.class) {
                if (Objects.isNull(cachedHandler)) {
                    ServiceLoader<CryptoStrategy> loader = ServiceLoader.load(CryptoStrategy.class);
                    CryptoStrategy strategy = null;
                    for (CryptoStrategy candidate : loader) {
                        strategy = candidate;
                        log.info("Discovered CryptoStrategy via ServiceLoader: {}", candidate.getClass().getName());
                        break;
                    }
                    cachedHandler = Objects.nonNull(strategy) ? new Ddd4jFieldCryptoHandler(strategy) : null;
                    if (Objects.isNull(cachedHandler)) {
                        log.warn("No CryptoStrategy found via ServiceLoader; @EncryptField will be skipped. " +
                                "Register a CryptoStrategy implementation in META-INF/services/ to enable encryption.");
                    }
                }
            }
        }
        return cachedHandler;
    }
}
