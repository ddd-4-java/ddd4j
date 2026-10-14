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
    /** 上下文中保存请求参数的键。 */
    public static final String REQUEST_PARAMS = "request-params";
    /** 上下文中保存请求体的键。 */
    public static final String REQUEST_BODY = "request-body";
    /** 上下文中保存租户标识的键。 */
    public static final String TENANT_ID = "tenant-id";
    /** 上下文中保存准备执行的 SQL的键。 */
    public static final String PREPARING_SQL = "preparing-sql";
    /** 上下文中保存SQL 参数的键。 */
    public static final String SQL_PARAMS = "sql-params";
    /** 上下文中保存上次 SQL 执行耗时的键。 */
    public static final String LAST_SQL_SPENDS = "last-sql-spends";
    /** 上下文中保存系统标识的键。 */
    public static final String SYSTEM_ID = "system-id";
    /** 上下文中保存门店标识的键。 */
    public static final String SHOP_ID = "shop-id";
    /** 上下文中保存角色的键。 */
    public static final String ROLE = "role";
    /** 上下文中保存语言区域的键。 */
    public static final String LOCALE = "locale";
    /** 上下文中保存第三方会话的键。 */
    public static final String THIRD_SESSION = "third-session";
    /** 上下文中保存当前会话的键。 */
    public static final String SESSION = "session";
    /** 上下文中保存客户端类型的键。 */
    public static final String CLIENT_TYPE = "client-type";
    /** 上下文中保存应用标识的键。 */
    public static final String APP_ID = "app-id";
    /** 上下文中保存用户标识的键。 */
    public static final String USER_ID = "user-id";
    /** 上下文中保存授权凭证的键。 */
    public static final String AUTHORIZATION = "Authorization";
    /** 上下文中保存项目根包名的键。 */
    public static String PROJECT_PACKAGE = "project-package";
}
