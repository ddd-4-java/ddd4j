/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.constant;

/**
 * 常用上下文对象常量名，本地线程持有
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ContextConstants {
    /**
     * 请求参数在上下文中的键名。
     */
    public static final String REQUEST_PARAMS = "request-params";
    /**
     * 请求体在上下文中的键名。
     */
    public static final String REQUEST_BODY = "request-body";
    /**
     * 租户标识在上下文中的键名。
     */
    public static final String TENANT_ID = "tenant-id";
    /**
     * 待执行 SQL（预编译语句）在上下文中的键名。
     */
    public static final String PREPARING_SQL = "preparing-sql";
    /**
     * SQL 绑定参数在上下文中的键名。
     */
    public static final String SQL_PARAMS = "sql-params";
    /**
     * 最近一次 SQL 耗时在上下文中的键名。
     */
    public static final String LAST_SQL_SPENDS = "last-sql-spends";
    /**
     * 系统标识在上下文中的键名。
     */
    public static final String SYSTEM_ID = "system-id";
    /**
     * 门店标识在上下文中的键名。
     */
    public static final String SHOP_ID = "shop-id";
    /**
     * 角色信息在上下文中的键名。
     */
    public static final String ROLE = "role";
    /**
     * 区域设置（Locale）在上下文中的键名。
     */
    public static final String LOCALE = "locale";
    /**
     * 第三方会话在上下文中的键名。
     */
    public static final String THIRD_SESSION = "third-session";
    /**
     * 会话信息在上下文中的键名。
     */
    public static final String SESSION = "session";
    /**
     * 客户端类型在上下文中的键名。
     */
    public static final String CLIENT_TYPE = "client-type";
    /**
     * 应用标识在上下文中的键名。
     */
    public static final String APP_ID = "app-id";
    /**
     * 用户标识在上下文中的键名。
     */
    public static final String USER_ID = "user-id";
    /**
     * 授权请求头名称。
     */
    public static final String AUTHORIZATION = "Authorization";
    /**
     * 工程包名在上下文中的键名。
     */
    public static String PROJECT_PACKAGE = "project-package";
}
