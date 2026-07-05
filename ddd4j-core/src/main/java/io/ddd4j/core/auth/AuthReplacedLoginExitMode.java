package io.ddd4j.core.auth;

/**
 * 在多人登录同一账号并发生挤占时，决定由哪一端放弃会话。
 *
 * <p>对应 Sa-Token 的 {@code SaReplacedLoginExitMode}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public enum AuthReplacedLoginExitMode {

    /**
     * 新登录设备挤掉旧登录设备（默认）。
     */
    NEW_DEVICE,

    /**
     * 旧登录设备保留，新登录失败。
     */
    OLD_DEVICE
}