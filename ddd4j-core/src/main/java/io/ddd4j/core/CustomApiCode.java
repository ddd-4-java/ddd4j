package io.ddd4j.core;

import io.ddd4j.core.constant.Constants;

/**
 * 自定义 API 错误码接口。
 * <p>
 * 业务方可通过实现此接口定义自己的业务错误码枚举，
 * 与 {@link ApiCode} 标准错误码共同构成统一的错误码体系。
 * <p>
 * 所有错误码包含三个属性：
 * <ul>
 *   <li>{@code code} — 数字错误码</li>
 *   <li>{@code reason} — 错误原因描述</li>
 *   <li>{@code status} — 响应状态标识（默认 success）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface CustomApiCode {

    /**
     * 获取错误码。
     *
     * @return 数字错误码
     */
    int getCode();

    /**
     * 获取错误原因描述。
     *
     * @return 错误原因
     */
    String getReason();

    /**
     * 获取响应状态标识。
     *
     * @return 状态标识（success / fail / error），默认 success
     */
    default String getStatus() {
        return Constants.RT_SUCCESS;
    }

}
