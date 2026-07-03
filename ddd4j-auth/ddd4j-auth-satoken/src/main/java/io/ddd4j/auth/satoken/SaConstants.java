package io.ddd4j.auth.satoken;

/**
 * Sa-Token 常量定义类。
 *
 * <p>集中管理项目中所有与 Sa-Token 相关的常量，包括 JWT 载荷字段、
 * 设备信息字段、API 签名头字段、以及临时 Token 相关字段等。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class SaConstants {

    /**
     * 临时 Token 的请求参数名称
     */
    public static final String PARAM_TEMP_TOKEN = "tempToken";

    /**
     * jwt签发者
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    public static final String PAYLOAD_ISSUER = "iss";
    /**
     * jwt所面向的用户
     */
    public static final String PAYLOAD_SUBJECT = "sub";
    /**
     * 接收jwt的一方
     */
    public static final String PAYLOAD_AUDIENCE = "aud";
    /**
     * jwt的过期时间，这个过期时间必须要大于签发时间
     */
    public static final String PAYLOAD_EXPIRES_AT = "exp";
    /**
     * 生效时间，定义在什么时间之前，该jwt都是不可用的.
     */
    public static final String PAYLOAD_NOT_BEFORE = "nbf";
    /**
     * jwt的签发时间
     */
    public static final String PAYLOAD_ISSUED_AT = "iat";
    /**
     * jwt的唯一身份标识，主要用来作为一次性token,从而回避重放攻击。
     */
    public static final String PAYLOAD_JWT_ID = "jti";

    /**
     * JWT 认证类型
     */
    public static final String PAYLOAD_AUTH_TYPE = "at";
    /**
     * JWT 学校/校区代码
     */
    public static final String PAYLOAD_SCHOOL_CODE = "xxdm";
    /**
     * JWT 用户 ID
     */
    public static final String PAYLOAD_USER_ID = "uid";
    /**
     * JWT 用户名称
     */
    public static final String PAYLOAD_USER_NAME = "uname";
    /**
     * JWT 用户代理信息
     */
    public static final String PAYLOAD_USER_AGENT = "ua";
    /**
     * JWT 机构/组织 ID
     */
    public static final String PAYLOAD_ORG_ID = "org_id";
    /**
     * JWT 学区/校区组织 ID
     */
    public static final String PAYLOAD_XQ_ORG_ID = "xq_org_id";
    /**
     * JWT 身份标识 ID
     */
    public static final String PAYLOAD_IDENTITY_ID = "iden_id";
    /**
     * JWT 信息条目 ID
     */
    public static final String PAYLOAD_INFO_ID = "info_id";
    /**
     * JWT 角色 ID
     */
    public static final String PAYLOAD_ROLE_ID = "rid";
    /**
     * JWT 账号标识
     */
    public static final String PAYLOAD_ACCOUNT = "acc";
    /**
     * JWT 父级 ID
     */
    public static final String PAYLOAD_PARENT_ID = "pid";
    /**
     * JWT 父级信息条目 ID
     */
    public static final String PAYLOAD_PARENT_INFO_ID = "p_info_id";

    /**
     * 设备类型字段名
     */
    public static final String FIELD_DEVICE_TYPE = "deviceType";
    /**
     * 设备 ID 字段名
     */
    public static final String FIELD_DEVICE_ID = "deviceId";
    /**
     * 应用 ID 字段名
     */
    public static final String FIELD_APP_ID = "appId";
    /**
     * 应用渠道字段名
     */
    public static final String FIELD_APP_CHANNEL = "appChannel";
    /**
     * 应用版本字段名
     */
    public static final String FIELD_APP_VERSION = "appVersion";

    /**
     * 移动端设备类型值
     */
    public final static String DEVICE_TYPE_MOBILE = "Mobile";
    /**
     * PC 端设备类型值
     */
    public final static String DEVICE_TYPE_PC = "PC";

    /**
     * API Key 请求头名称
     */
    public final static String HEAD_API_KEY = "apiKey";
    /**
     * 时间戳请求头名称
     */
    public final static String HEAD_TIMESTAMP = "timestamp";
    /**
     * 签名请求头名称
     */
    public final static String HEAD_SIGNATURE = "signature";

    /**
     * 一次性密码（OTP）字段名
     */
    public static final String FIELD_OTP = "otp";
    /**
     * 验证码字段名
     */
    public static final String FIELD_CODE = "code";
    /**
     * 状态字段名
     */
    public static final String FIELD_STATUS = "status";
    /**
     * Token 字段名
     */
    public static final String FIELD_TOKEN = "token";

}
