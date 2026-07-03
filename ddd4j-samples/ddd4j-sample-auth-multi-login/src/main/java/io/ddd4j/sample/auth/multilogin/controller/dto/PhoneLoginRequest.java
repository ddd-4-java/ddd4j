package io.ddd4j.sample.auth.multilogin.controller.dto;

/**
 * 手机号登录请求参数。
 *
 * @param phone      手机号
 * @param code       验证码
 * @param deviceType 设备类型（如 iOS、Android、Web）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record PhoneLoginRequest(String phone, String code, String deviceType) {
}
