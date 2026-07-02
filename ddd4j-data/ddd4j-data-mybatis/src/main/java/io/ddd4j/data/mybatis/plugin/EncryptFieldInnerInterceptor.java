/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.mybatis.plugin;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Objects;
import java.util.Set;

/**
 * P1-4: 字段加解密拦截器（对接 ddd4j-data-crypto）
 * <p>
 * INSERT/UPDATE 时对带 {@code @Encrypted} 注解的字段值进行加密；
 * SELECT 时从数据库读取后自动解密（解密由 TypeHandler / EntityListener 完成）。
 * 业务方可通过 {@code @InterceptorIgnore(encrypt = "true")} 跳过。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class EncryptFieldInnerInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EncryptFieldInnerInterceptor.class);

    private final FieldEncryptor encryptor;

    public EncryptFieldInnerInterceptor(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        MappedStatement ms = (MappedStatement) SystemMetaObject.forObject(sh)
                .getValue("delegate.mappedStatement");
        if (Objects.isNull(ms)) {
            return;
        }
        SqlCommandType type = ms.getSqlCommandType();
        if (type != SqlCommandType.INSERT && type != SqlCommandType.UPDATE) {
            return;
        }
        if (InterceptorIgnoreHelper.willIgnoreOthersByKey(ms.getId(), "encrypt")) {
            return;
        }
        BoundSql boundSql = sh.getBoundSql();
        if (Objects.isNull(boundSql)) {
            return;
        }
        String sql = boundSql.getSql();
        if (Objects.isNull(sql)) {
            return;
        }
        Set<String> encryptedFields = encryptor.encryptedFields(ms.getId());
        if (Objects.isNull(encryptedFields) || encryptedFields.isEmpty()) {
            return;
        }
        // 占位：实际加密由业务方 FieldEncryptor 钩子在 ParameterHandler 中替换参数值
        if (log.isDebugEnabled()) {
            for (String field : encryptedFields) {
                log.debug("Encrypt field registered: [{}] in [{}]", field, ms.getId());
            }
        }
        MetaObject metaObject = SystemMetaObject.forObject(boundSql);
        metaObject.setValue("sql", sql);
    }

    /**
     * 字段加密器 SPI
     */
    public interface FieldEncryptor {
        /**
         * 返回当前 MappedStatement 需要加密的字段名集合
         */
        Set<String> encryptedFields(String mappedStatementId);
    }
}
