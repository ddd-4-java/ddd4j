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

/** \u9a8c\u8bc1 <code>Ddd4jMybatisConfigurerEncryption</code> \u5728 <code>configuredHandlerEncryptsOnWriteAnd</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class Ddd4jMybatisConfigurerEncryptionTest {
    /**
     * \u9a8c\u8bc1<code>rows.next()</code>\u4e3a\u771f\uff0c<code>mapper.read().getSecret()</code> \u7b49\u4e8e <code>"private value"</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>PersistenceException.class</code>\u3002
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u63d0\u4f9b <code>insert()</code>\u3001<code>read()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    interface EntryMapper {
        /**
         * \u628a\u7ed9\u5b9a\u5939\u5177\u6570\u636e\u63d2\u5165\u5f53\u524d\u6d4b\u8bd5\u8868\u3002
         * @param entry \u8981\u5411\u5f53\u524d\u6d4b\u8bd5\u8868\u5199\u5165\u7684\u5939\u5177\u5bf9\u8c61
         * @return \u63d2\u5165\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @Insert("INSERT INTO crypto_entry(secret, signature) VALUES(#{secret}, #{signature})")
        int insert(Entry entry);

        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u3002
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("SELECT secret, signature FROM crypto_entry")
        Entry read();
    }

    /** \u5c06 <code>secret</code>\u3001<code>signature</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    @Data
    @EncryptedTable
    @TableSignature
    public static class Entry {
        /** \u5f53\u524d\u7b7e\u540d\u3001\u52a0\u5bc6\u6216\u4ee4\u724c\u6821\u9a8c\u6d4b\u8bd5\u4f7f\u7528\u7684\u5bc6\u94a5\u6837\u672c\u3002 */
        @EncryptedField
        @TableSignatureField
        private String secret;
        /** \u9a8c\u7b7e\u3001\u52a0\u5bc6\u5b57\u6bb5\u6216\u6301\u4e45\u5316\u5f80\u8fd4\u6d4b\u8bd5\u4f7f\u7528\u7684\u7b7e\u540d\u503c\u3002 */
        @TableSignatureField(stored = true)
        private String signature;
    }
}
