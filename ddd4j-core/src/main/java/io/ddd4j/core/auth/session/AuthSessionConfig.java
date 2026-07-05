package io.ddd4j.core.auth.session;

import io.ddd4j.core.auth.AuthLogoutMode;
import io.ddd4j.core.auth.AuthReplacedLoginExitMode;
import io.ddd4j.core.auth.AuthReplacedRange;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 登录会话的策略配置（框架无关）。
 *
 * <p>对齐 Sa-Token 的 {@code SaLoginParameter}，但去掉了框架特定字段（如 sa-token 的
 * {@code maxTryTimes}、{@code rightNowCreateTokenSession}），保留通用会话策略能力。
 *
 * <p>具体鉴权框架实现（SaToken/Shiro/Security）负责将通用策略映射为框架原生参数。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class AuthSessionConfig {

    // ==================== 单独参数 ====================

    /**
     * 此次登录的客户端设备类型（多端登录隔离用）。
     * <p>默认 null，由各框架决定默认值（SaToken 默认 "default"）。
     */
    private String deviceType;

    /**
     * 此次登录的客户端设备 id（用于信任设备识别）。
     */
    private String deviceId;

    /**
     * 扩展信息（写入 Token Claim 或 Session）。
     */
    private AuthCookieConfig cookie = new AuthCookieConfig();

    // ==================== 会话生命周期 ====================

    /**
     * Token 有效期（秒），-1 表示永久。
     */
    private long timeout = -1;

    /**
     * Token 最低活跃频率（秒），null 表示不启用活跃检查。
     */
    private Long activeTimeout;

    /**
     * 登录后立即创建 TokenSession（默认 false，第一次调用时懒创建）。
     */
    private boolean createTokenSessionNow;

    // ==================== 多端登录策略 ====================

    /**
     * 是否允许同一账号多地同时登录（true=允许，false=挤掉旧登录，默认 true）。
     */
    private boolean concurrent = true;

    /**
     * 同一账号多地登录时，是否共用一个 token（true=共用，false=每次新 token，默认 true）。
     */
    private boolean share = true;

    /**
     * 同一账号最大登录数量，-1 代表不限（仅当 concurrent=true 且 share=false 时有意义）。
     */
    private int maxLoginCount = -1;

    /**
     * 在挤占发生时，决定由哪一端放弃会话（默认 NEW_DEVICE）。
     */
    private AuthReplacedLoginExitMode replacedLoginExitMode = AuthReplacedLoginExitMode.NEW_DEVICE;

    /**
     * 挤占会话的范围（默认 CURRENT_DEVICE_TYPE）。
     */
    private AuthReplacedRange replacedRange = AuthReplacedRange.CURRENT_DEVICE_TYPE;

    /**
     * 超过 maxLoginCount 的设备，下线处理方式（默认 LOGOUT）。
     */
    private AuthLogoutMode overflowLogoutMode = AuthLogoutMode.LOGOUT;

    // ==================== 输出配置 ====================

    /**
     * Token 是否写入响应头（默认 true）。
     */
    private boolean writeTokenToHeader = true;

    /**
     * 预定 Token 值（null 时由框架生成）。
     */
    private String presetToken;
}