package io.ddd4j.sample.auth.multilogin.controller.dto;

public record ThirdPartyLoginRequest(String provider, String openId, String unionId) {
}
