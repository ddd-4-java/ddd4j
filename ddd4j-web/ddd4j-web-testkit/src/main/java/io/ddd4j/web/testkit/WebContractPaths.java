package io.ddd4j.web.testkit;

/**
 * 所有 Web 适配器契约测试共享的端点路径。
 */
public final class WebContractPaths {

    public static final String SUCCESS = "/contract/success";
    public static final String CREATED = "/contract/created";
    public static final String PUBLIC = "/contract/public";
    public static final String PROTECTED = "/contract/protected";
    public static final String CONTEXT = "/contract/context";
    public static final String IDEMPOTENT = "/contract/idempotent";
    public static final String BAD_REQUEST = "/contract/errors/bad-request";
    public static final String FORBIDDEN = "/contract/errors/forbidden";
    public static final String NOT_FOUND = "/contract/errors/not-found";
    public static final String CONFLICT = "/contract/errors/conflict";
    public static final String UNSUPPORTED_MEDIA_TYPE = "/contract/errors/unsupported-media-type";
    public static final String UNPROCESSABLE_ENTITY = "/contract/errors/unprocessable-entity";
    public static final String TOO_MANY_REQUESTS = "/contract/errors/too-many-requests";
    public static final String INTERNAL_SERVER_ERROR = "/contract/errors/internal-server-error";

    private WebContractPaths() {
    }
}
