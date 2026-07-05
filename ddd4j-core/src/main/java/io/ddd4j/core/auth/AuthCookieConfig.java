package io.ddd4j.core.auth;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 框架无关的 Cookie 配置。
 *
 * <p>对应 Sa-Token 的 {@code SaCookieConfig}。
 *
 * <p>具体鉴权框架实现（SaToken/Shiro/Security）负责将通用配置映射到框架原生配置：
 * <ul>
 *   <li>SaToken → 写到 SaCookieConfig 对应字段（domain/path/secure/sameSite）</li>
 *   <li>Shiro → 由 ShiroCookie 解析</li>
 *   <li>Security → 由 RememberMeServices 解析</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class AuthCookieConfig {

    /**
     * Cookie 名称（默认 "satoken"）。
     */
    private String name;

    /**
     * Cookie 作用域名（默认当前域名）。
     */
    private String domain;

    /**
     * Cookie 作用路径（默认 "/"）。
     */
    private String path = "/";

    /**
     * 是否仅 HTTPS 传输（默认 false）。
     */
    private boolean secure;

    /**
     * 是否仅 HTTP 访问，禁止 JS 读取（默认 true，与 SaToken 默认一致）。
     */
    private boolean httpOnly = true;

    /**
     * SameSite 策略：{@code Strict}/{@code Lax}/{@code None}。
     */
    private String sameSite = "Lax";

    /**
     * Cookie 有效期（秒），-1 表示会话级（浏览器关闭时失效）。
     */
    private long maxAge = -1;
}