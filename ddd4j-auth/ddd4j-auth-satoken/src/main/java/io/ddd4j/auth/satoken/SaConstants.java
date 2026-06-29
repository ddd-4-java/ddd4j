package io.ddd4j.auth.satoken;

public final class SaConstants {

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

    public static final String PAYLOAD_AUTH_TYPE = "at";
    public static final String PAYLOAD_SCHOOL_CODE = "xxdm";
    public static final String PAYLOAD_USER_ID = "uid";
    public static final String PAYLOAD_USER_NAME = "uname";
    public static final String PAYLOAD_USER_AGENT = "ua";
    public static final String PAYLOAD_ORG_ID = "org_id";
    public static final String PAYLOAD_XQ_ORG_ID = "xq_org_id";
    public static final String PAYLOAD_IDENTITY_ID = "iden_id";
    public static final String PAYLOAD_INFO_ID = "info_id";
    public static final String PAYLOAD_ROLE_ID = "rid";
    public static final String PAYLOAD_ACCOUNT = "acc";
    public static final String PAYLOAD_PARENT_ID = "pid";
    public static final String PAYLOAD_PARENT_INFO_ID = "p_info_id";

    public static final String FIELD_DEVICE_TYPE = "deviceType";
    public static final String FIELD_DEVICE_ID = "deviceId";
    public static final String FIELD_APP_ID = "appId";
    public static final String FIELD_APP_CHANNEL = "appChannel";
    public static final String FIELD_APP_VERSION = "appVersion";

    public final static String DEVICE_TYPE_MOBILE = "Mobile";
    public final static String DEVICE_TYPE_PC = "PC";

    public final static String HEAD_API_KEY = "apiKey";
    public final static String HEAD_TIMESTAMP = "timestamp";
    public final static String HEAD_SIGNATURE = "signature";

    public static final String FIELD_OTP = "otp";
    public static final String FIELD_CODE = "code";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_TOKEN = "token";

}
