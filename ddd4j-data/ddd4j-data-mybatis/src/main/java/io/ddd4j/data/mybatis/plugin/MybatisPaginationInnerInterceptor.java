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

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * P1-4: 分页拦截器（MyBatis-Plus 3.5+ 推荐写法）
 * <p>
 * 相比 3.4.x 的 PaginationInterceptor 拆分为 InnerInterceptor 体系。
 * 业务模块启用方式：MybatisPlusInterceptor.addInnerInterceptor(new MybatisPaginationInnerInterceptor(DbType.MYSQL))
 * </p>
 *
 * @author hiwepy
 */
public class MybatisPaginationInnerInterceptor extends PaginationInnerInterceptor {

    public MybatisPaginationInnerInterceptor(DbType dbType) {
        super(dbType);
    }

    public MybatisPaginationInnerInterceptor(DbType dbType, com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect dialect) {
        super(dialect);
    }
}
