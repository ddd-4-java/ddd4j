package io.ddd4j.auth.satoken.util;

import cn.dev33.satoken.error.SaErrorCode;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.temp.SaTempUtil;
import io.ddd4j.auth.satoken.SaTempToken;

import java.util.Objects;

/**
 * 临时 Token 工具类，封装 Sa-Token {@link SaTempUtil} 的常用操作。
 *
 * <p>提供临时 Token 的创建、解析、过期时间查询、删除及校验等静态方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SaTempKit {

    private SaTempKit() {
    }

    // -------- 创建

    /**
     * 为指定 value 创建一个临时 Token
     *
     * @param value   指定值
     * @param timeout 有效期，单位：秒，-1 代表永久有效
     * @return 生成的token
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    public static String createToken(SaTempToken value, long timeout) {
        return SaTempUtil.createToken(value, timeout);
    }

    // -------- 解析

    /**
     * 解析 Token 获取 value
     *
     * @param token 指定 Token
     * @return /
     */
    public static SaTempToken parseToken(String token) {
        return SaTempUtil.parseToken(token, SaTempToken.class);
    }

    /**
     * 解析 Token 获取 value
     *
     * @param service 业务标识
     * @param token   指定 Token
     * @return /
     */
    public static SaTempToken parseToken(String service, String token) {
        return SaTempUtil.parseToken(service, token, SaTempToken.class);
    }


    /**
     * 获取指定 Token 的剩余有效期，单位：秒
     * <p> 返回值 -1 代表永久，-2 代表token无效
     *
     * @param token 指定 Token
     * @return /
     */
    public static long getTimeout(String token) {
        return SaTempUtil.getTimeout(token);
    }

    // -------- 删除

    /**
     * 删除一个 Token
     *
     * @param token 指定 Token
     */
    public static void deleteToken(String token) {
        SaTempUtil.deleteToken(token);
    }

    // -------- 检查

    /**
     * 校验临时 Token 字符串，并返回解析后的 {@link SaTempToken} 对象。
     *
     * <p>校验内容包括：Token 不为空、未过期、格式有效、登录 ID 不为空。
     *
     * @param tempToken 临时 Token 字符串
     * @return 解析后的 SaTempToken 对象
     * @throws SaTokenException 校验失败时抛出对应异常
     */
    public static SaTempToken checkTempToken(String tempToken) {
        if (Objects.isNull(tempToken) || !io.ddd4j.kit.lang.StrKit.isNotBlank(tempToken)) {
            throw new SaTokenException(SaErrorCode.CODE_11001, "未能读取到有效Token");
        }
        // 获取指定 业务标识、指定 Token 的剩余有效期，单位：秒
        long timeout = SaTempKit.getTimeout(tempToken);
        // 返回值 -1 代表永久，-2 代表token无效
        if (timeout == -2) {
            // 校验不通过，则抛出异常
            throw new SaTokenException(SaErrorCode.CODE_11013, "Token已过期，未通过校验");
        }
        SaTempToken saTempToken = SaTempKit.parseToken(tempToken);
        if (Objects.isNull(saTempToken)) {
            throw new SaTokenException(SaErrorCode.CODE_11012, "无效的Token，未通过校验");
        }
        // 检查登录时的账号id值是否为空
        if (Objects.isNull(saTempToken.getLoginId())
                || !io.ddd4j.kit.lang.StrKit.isNotBlank(saTempToken.getLoginId())) {
            throw new SaTokenException(SaErrorCode.CODE_11002, "登录时的账号id值为空");
        }
        return saTempToken;
    }

    /**
     * 校验已解析的 {@link SaTempToken} 对象。
     *
     * <p>校验内容包括：对象不为空、登录 ID 不为空。
     *
     * @param saTempToken 已解析的 SaTempToken 对象
     * @return 校验通过返回原对象
     * @throws SaTokenException 校验失败时抛出对应异常
     */
    public static SaTempToken checkTempToken(SaTempToken saTempToken) {
        if (Objects.isNull(saTempToken)) {
            throw new SaTokenException(SaErrorCode.CODE_11012, "无效的Token，未通过校验");
        }
        // 检查登录时的账号id值是否为空
        if (Objects.isNull(saTempToken.getLoginId())
                || !io.ddd4j.kit.lang.StrKit.isNotBlank(saTempToken.getLoginId())) {
            throw new SaTokenException(SaErrorCode.CODE_11002, "登录时的账号id值为空");
        }
        return saTempToken;
    }
}
