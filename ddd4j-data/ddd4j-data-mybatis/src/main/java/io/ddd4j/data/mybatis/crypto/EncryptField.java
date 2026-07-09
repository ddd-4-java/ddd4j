package io.ddd4j.data.mybatis.crypto;

import java.lang.annotation.*;

/**
 * 等保数据加密字段注解。
 *
 * <p>标注在 PO 字段上，写入数据库时自动加密，读取时自动解密。
 * 配合 {@link Ddd4jCryptoInterceptor} 使用。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * public class UserPO {
 *     @EncryptField
 *     private String idCard;
 *
 *     @EncryptField
 *     private String phone;
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface EncryptField {
}
