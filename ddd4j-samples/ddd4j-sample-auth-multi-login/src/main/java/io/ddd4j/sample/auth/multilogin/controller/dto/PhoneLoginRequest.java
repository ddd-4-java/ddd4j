package io.ddd4j.sample.auth.multilogin.controller.dto;

public record PhoneLoginRequest(String phone, String code, String deviceType) {
}
