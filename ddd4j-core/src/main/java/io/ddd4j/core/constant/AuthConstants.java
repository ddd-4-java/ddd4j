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
 * 鉴权层通用常量（框架无关）。
 *
 * <p>集中管理跨三鉴权实现（SaToken/Shiro/Security）共享的常量，包括：
 * <ul>
 *   <li>JWT 标准字段名（RFC 7519）</li>
 *   <li>设备类型、设备字段</li>
 *   <li>API 签名/认证 HTTP 头名</li>
 *   <li>鉴权请求/响应常见字段名</li>
 * </ul>
 *
 * <p>应用专用的载荷字段由应用维护。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class AuthConstants {

    /**
     * jwt 签发者（issuer，对齐 JWT spec "iss"）。
     */
    public static final String JWT_ISSUER = "iss";

    // ==================== JWT 标准字段（RFC 7519）====================
    /**
     * jwt 所面向的用户（subject，对齐 JWT spec "sub"）。
     */
    public static final String JWT_SUBJECT = "sub";
    /**
     * 接收 jwt 的一方（audience，对齐 JWT spec "aud"）。
     */
    public static final String JWT_AUDIENCE = "aud";
    /**
     * jwt 过期时间（expiration time，对齐 JWT spec "exp"）。
     */
    public static final String JWT_EXPIRES_AT = "exp";
    /**
     * 生效时间（not before，对齐 JWT spec "nbf"）。
     */
    public static final String JWT_NOT_BEFORE = "nbf";
    /**
     * jwt 签发时间（issued at，对齐 JWT spec "iat"）。
     */
    public static final String JWT_ISSUED_AT = "iat";
    /**
     * jwt 唯一身份标识（jwt id，对齐 JWT spec "jti"，用于防重放）。
     */
    public static final String JWT_JWT_ID = "jti";
    /**
     * JWT 认证类型（auth type，扩展字段，区分不同认证模式）。
     */
    public static final String JWT_AUTH_TYPE = "at";
    /**
     * 临时 Token 的请求参数名（一次性密码场景）。
     */
    public static final String PARAM_TEMP_TOKEN = "tempToken";

    // ==================== Token 通用字段 ====================
    /**
     * Token 字段名（响应体 / 通用 token 字段）。
     */
    public static final String FIELD_TOKEN = "token";
    /**
     * 设备类型字段名。
     */
    public static final String FIELD_DEVICE_TYPE = "deviceType";

    // ==================== 设备字段 ====================
    /**
     * 设备 ID 字段名。
     */
    public static final String FIELD_DEVICE_ID = "deviceId";
    /**
     * 移动端设备类型值。
     */
    public static final String DEVICE_TYPE_MOBILE = "Mobile";
    /**
     * PC 端设备类型值。
     */
    public static final String DEVICE_TYPE_PC = "PC";
    /**
     * 应用 ID 字段名。
     */
    public static final String FIELD_APP_ID = "appId";

    // ==================== 应用字段 ====================
    /**
     * 应用渠道字段名。
     */
    public static final String FIELD_APP_CHANNEL = "appChannel";
    /**
     * 应用版本字段名。
     */
    public static final String FIELD_APP_VERSION = "appVersion";
    /**
     * API Key 请求头名称。
     */
    public static final String HEADER_API_KEY = "apiKey";

    // ==================== API 签名/认证 HTTP 头 ====================
    /**
     * 时间戳请求头名称。
     */
    public static final String HEADER_TIMESTAMP = "timestamp";
    /**
     * 签名请求头名称。
     */
    public static final String HEADER_SIGNATURE = "signature";
    /**
     * 用户 ID 字段名。
     */
    public static final String FIELD_USER_ID = "uid";

    // ==================== 通用业务字段 ====================
    /**
     * 用户名称字段名。
     */
    public static final String FIELD_USER_NAME = "uname";
    /**
     * 角色 ID 字段名。
     */
    public static final String FIELD_ROLE_ID = "rid";
    /**
     * 角色编码字段名。
     */
    public static final String FIELD_ROLE_CODE = "roleCode";
    /**
     * 角色类型字段名。
     */
    public static final String FIELD_ROLE_TYPE = "roleType";
    /**
     * 租户 ID 字段名。
     */
    public static final String FIELD_TENANT_ID = "tenant_id";
    /**
     * 机构 ID 字段名。
     */
    public static final String FIELD_ORG_ID = "org_id";
    /**
     * 父级 ID 字段名。
     */
    public static final String FIELD_PARENT_ID = "pid";
    /**
     * 账号标识字段名。
     */
    public static final String FIELD_ACCOUNT = "acc";
    /**
     * 一次性密码（OTP）字段名。
     */
    public static final String FIELD_OTP = "otp";

    // ==================== JWT 载荷字段 ====================
    /**
     * JWT 租户 ID 的约定键。
     */
    public static final String CLAIM_TENANT_ID = "tenant_id";
    /**
     * JWT 用户 ID 的约定键。
     */
    public static final String CLAIM_USER_ID = "uid";
    /**
     * JWT 角色 ID 的约定键。
     */
    public static final String CLAIM_ROLE_ID = "rid";
    /**
     * JWT 机构 ID 的约定键。
     */
    public static final String CLAIM_ORG_ID = "org_id";

    // ==================== 验证码/状态 ====================
    /**
     * 验证码字段名。
     */
    public static final String FIELD_CODE = "code";
    /**
     * 状态字段名。
     */
    public static final String FIELD_STATUS = "status";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AuthConstants</code> \u5b9e\u4f8b\u3002 */
    private AuthConstants() {
    }
}
