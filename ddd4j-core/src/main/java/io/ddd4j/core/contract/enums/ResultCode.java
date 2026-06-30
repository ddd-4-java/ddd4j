package io.ddd4j.core.contract.enums;

import lombok.Getter;

/**
 * 常用错误码定义
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public enum ResultCode implements IEnum<Integer> {
    OK(0, "请求/操作成功"),
    SUCCESS(200, "请求/操作成功"),
    FAIL(1, "请求成功但是服务异常"),
    BAD_REQUEST(400, "请求异常"),
    UNAUTHORIZED(401, "未登录或token已经失效"),
    FORBIDDEN(403, "没有权限"),
    NO_ACCESS_TOKEN(403001, "没有权限，缺少AccessToken"),
    SERVER_ERROR(500, "服务器异常"),
    PARAMETER_VALIDATION_FAILED(400100, "参数校验失败"),
    TIMEOUT(60001, "登录超时，请重新登录"),
    NO_SESSION(60002, "session不能为空"),
    LOGIN_FIRST(60003, "请先登录"),
    ;

    private Integer code;
    private String desc;

    private ResultCode(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(Integer code) {
        String desc = "";
        ResultCode[] var2 = values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            ResultCode codeEnum = var2[var4];
            if (codeEnum.getCode().equals(code)) {
                desc = codeEnum.getDesc();
                break;
            }
        }

        return desc;
    }

    public static ResultCode getByCode(Integer code) {
        ResultCode resultCode = null;
        ResultCode[] var2 = values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            ResultCode codeEnum = var2[var4];
            if (codeEnum.getCode().equals(code)) {
                resultCode = codeEnum;
                break;
            }
        }

        return resultCode;
    }
}