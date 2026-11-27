package io.ddd4j.data.mybatis.config;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.enhance.annotation.crypto.EncryptedField;
import org.apache.ibatis.enhance.annotation.crypto.EncryptedTable;
import org.apache.ibatis.enhance.annotation.crypto.TableSignature;
import org.apache.ibatis.enhance.annotation.crypto.TableSignatureField;
import org.apache.ibatis.enhance.crypto.enums.SymmetricAlgorithmType;
import org.apache.ibatis.enhance.crypto.handler.DefaultEncryptedFieldHandler;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 <code>Ddd4jMybatisConfigurerEncryption</code> 在 <code>configuredHandlerEncryptsOnWriteAnd</code> 等场景中的行为。
 */
class Ddd4jMybatisConfigurerEncryptionTest {
    /**
     * 验证<code>rows.next()</code>为真，<code>mapper.read().getSecret()</code> 等于 <code>"private value"</code>，被测调用 抛出 <code>PersistenceException.class</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
    @Test
    void configuredHandlerEncryptsOnWriteAndRejectsAnInvalidSignatureOnRead() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:native_crypto");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE crypto_entry(secret VARCHAR(2048), signature VARCHAR(2048))");
            var handler = new DefaultEncryptedFieldHandler(new ObjectMapper(), SymmetricAlgorithmType.AES,
                    HmacAlgorithm.HmacSHA256, Mode.CBC, Padding.PKCS5Padding,
                    Base64.encode("example-test-key"), Base64.encode("example-test--iv"));
            var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
            config.addInterceptor(new Ddd4jMybatisConfigurer().encryption(handler).signature(true).build());
            config.addMapper(EntryMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(config);
            try (var session = factory.openSession(true)) {
                var mapper = session.getMapper(EntryMapper.class);
                var entry = new Entry();
                entry.setSecret("private value");
                mapper.insert(entry);
                try (var rows = statement.executeQuery("SELECT secret, signature FROM crypto_entry")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNotBlank().isNotEqualTo("private value");
                    assertThat(rows.getString(2)).isNotBlank();
                }
                assertThat(mapper.read().getSecret()).isEqualTo("private value");
                statement.executeUpdate("UPDATE crypto_entry SET signature = 'tampered'");
                session.clearCache();
                assertThatThrownBy(mapper::read).isInstanceOf(PersistenceException.class)
                        .hasMessageContaining("signature mismatch");
            }
        }
    }

    /**
     * 提供 <code>insert()</code>、<code>read()</code> 等供当前测试使用的独立操作。
     */
    interface EntryMapper {
        /**
         * 把给定夹具数据插入当前测试表。
         *
         * @param entry 要向当前测试表写入的夹具对象
         * @return 插入操作影响的行数
         */
        @Insert("INSERT INTO crypto_entry(secret, signature) VALUES(#{secret}, #{signature})")
        int insert(Entry entry);

        /**
         * 按映射 SQL 读取测试记录。
         *
         * @return 与查询匹配的映射结果
         */
        @Select("SELECT secret, signature FROM crypto_entry")
        Entry read();
    }

    /**
     * 将 <code>secret</code>、<code>signature</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    @Data
    @EncryptedTable
    @TableSignature
    public static class Entry {
        /**
         * 当前签名、加密或令牌校验测试使用的密钥样本。
         */
        @EncryptedField
        @TableSignatureField
        private String secret;
        /**
         * 验签、加密字段或持久化往返测试使用的签名值。
         */
        @TableSignatureField(stored = true)
        private String signature;
    }
}
