package io.ddd4j.sample.auth.multilogin.controller.dto;

/**
 * 第三方平台登录请求参数。
 *
 * @param provider 第三方平台标识（如 wechat、qq、weibo）
 * @param openId   用户在第三方平台的 OpenID
 * @param unionId  用户在第三方平台的 UnionID（可选，用于跨应用识别用户）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record ThirdPartyLoginRequest(String provider, String openId, String unionId) {
}
