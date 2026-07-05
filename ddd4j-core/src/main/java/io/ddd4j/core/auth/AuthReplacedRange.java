package io.ddd4j.core.auth;

/**
 * 当挤占发生时，被挤占会话的范围。
 *
 * <p>对应 Sa-Token 的 {@code SaReplacedRange}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public enum AuthReplacedRange {

    /**
     * 仅当前设备类型被挤占下线（默认）。
     */
    CURRENT_DEVICE_TYPE,

    /**
     * 所有设备类型均被挤占下线。
     */
    ALL_DEVICE_TYPE
}