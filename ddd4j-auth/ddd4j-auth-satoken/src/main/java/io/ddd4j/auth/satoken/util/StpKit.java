package io.ddd4j.auth.satoken.util;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.auth.satoken.SaConstants;
import io.ddd4j.core.constant.AuthConstants;
import io.ddd4j.kit.lang.FunctionKit;
import io.ddd4j.kit.lang.JsonKit;

import java.util.Objects;
import java.util.function.Function;

/**
 * StpLogic 门面类，管理项目中所有的 StpLogic 账号体系
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class StpKit {

    /**
     * 默认原生会话对象
     */
    public static final StpLogic DEFAULT = StpUtil.stpLogic;

    /**
     * Admin 会话对象，管理 Admin 表所有账号的登录、权限认证
     */
    public static final StpLogic ADMIN = new StpLogic("admin");

    /**
     * User 会话对象，管理 User 表所有账号的登录、权限认证
     */
    public static final StpLogic USER = new StpLogic("user");

    /**
     * XX 会话对象，（项目中有多少套账号表，就声明几个 StpLogic 会话对象）
     */
    public static final StpLogic XXX = new StpLogic("xx");

    /**
     * 获取当前会话账号id, 并转换为 long 类型
     *
     * @return 账号id
     */
    public static Long getLoginIdAsLong() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前会话账号id, 并转换为 String 类型
     *
     * @return 账号id
     */
    public static String getLoginIdAsString() {
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 获取当前会话账号id, 并转换为 int 类型
     *
     * @return 账号id
     */
    public static Integer getLoginIdAsInteger() {
        return StpUtil.getLoginIdAsInt();
    }

    /**
     * 获取当前会话的用户 ID（从 JWT 扩展信息中读取）。
     *
     * @return 用户 ID
     */
    public static Object getUserId() {
        return StpUtil.getExtra(AuthConstants.FIELD_USER_ID);
    }

    /**
     * 获取当前会话的用户 ID（long 类型）。
     *
     * @return 用户 ID
     */
    public static Long getUserIdAsLong() {
        return getExtraAs(AuthConstants.FIELD_USER_ID, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前会话的用户 ID（String 类型）。
     *
     * @return 用户 ID
     */
    public static String getUserIdAsString() {
        return getExtraAs(AuthConstants.FIELD_USER_ID, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的用户 ID（Integer 类型）。
     *
     * @return 用户 ID
     */
    public static Integer getUserIdAsInteger() {
        return getExtraAs(AuthConstants.FIELD_USER_ID, FunctionKit.TO_INTEGER);
    }

    /**
     * 获取当前会话的机构/组织 ID。
     *
     * @return 机构 ID
     */
    public static Object getOrgId() {
        return StpUtil.getExtra(AuthConstants.FIELD_ORG_ID);
    }

    /**
     * 获取当前会话的机构/组织 ID（String 类型）。
     *
     * @return 机构 ID
     */
    public static String getOrgIdAsString() {
        return getExtraAs(AuthConstants.FIELD_ORG_ID, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的机构/组织 ID（Integer 类型）。
     *
     * @return 机构 ID
     */
    public static Integer getOrgIdAsInteger() {
        return getExtraAs(AuthConstants.FIELD_ORG_ID, FunctionKit.TO_INTEGER);
    }

    /**
     * 获取当前会话的机构/组织 ID（Long 类型）。
     *
     * @return 机构 ID
     */
    public static Long getOrgIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前会话的校区组织 ID（String 类型）。
     *
     * @return 校区组织 ID
     */
    public static String getXqOrgIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_XQ_ORG_ID, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的校区组织 ID（Integer 类型）。
     *
     * @return 校区组织 ID
     */
    public static Integer getXqOrgIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_XQ_ORG_ID, FunctionKit.TO_INTEGER);
    }

    /**
     * 获取当前会话的校区组织 ID（Long 类型）。
     *
     * @return 校区组织 ID
     */
    public static Long getXqOrgIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_XQ_ORG_ID, FunctionKit.TO_LONG);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u4f1a\u8bdd\u7684\u4fe1\u606f\u6761\u76ee ID\uff08Long \u7c7b\u578b\uff09\u3002
     * @return \u4fe1\u606f\u6761\u76ee ID
     *
     * @throws NumberFormatException \u5b57\u6bb5\u503c\u65e0\u6cd5\u8f6c\u6362\u4e3a Long \u65f6\u629b\u51fa
     */
    public static Long getInfoIdAsLong() {
        return getExtraAs(AuthConstants.FIELD_ORG_ID, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前会话的信息条目 ID（String 类型）。
     *
     * @return 信息条目 ID
     */
    public static String getInfoIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的信息条目 ID（Integer 类型）。
     *
     * @return 信息条目 ID
     */
    public static Integer getInfoIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, FunctionKit.TO_INTEGER);
    }

    /**
     * 获取当前会话的角色 ID。
     *
     * @return 角色 ID
     */
    public static Object getRoleId() {
        return StpUtil.getExtra(SaConstants.PAYLOAD_INFO_ID);
    }

    /**
     * 获取当前会话的角色 ID（String 类型）。
     *
     * @return 角色 ID
     */
    public static String getRoleIdAsString() {
        return getExtraAs(AuthConstants.FIELD_ROLE_ID, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的角色 ID（Integer 类型）。
     *
     * @return 角色 ID
     */
    public static Integer getRoleIdAsInteger() {
        return getExtraAs(AuthConstants.FIELD_ROLE_ID, FunctionKit.TO_INTEGER);
    }

    /**
     * 获取当前会话的角色 ID（Long 类型）。
     *
     * @return 角色 ID
     */
    public static Long getRoleIdAsLong() {
        return getExtraAs(AuthConstants.FIELD_ROLE_ID, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前会话的学校/校区代码（String 类型）。
     *
     * @return 学校代码
     */
    public static String getXxdmAsString() {
        return getExtraAs(SaConstants.PAYLOAD_SCHOOL_CODE, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的身份标识 ID（Integer 类型）。
     *
     * @return 身份标识 ID
     */
    public static Integer getIdentityIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_IDENTITY_ID, FunctionKit.TO_INTEGER);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u8f6c\u6362\u4e3a String \u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @return \u8d26\u53f7id
     *
     * @param key \u6269\u5c55\u4fe1\u606f\u952e
     */
    public static String getExtraAsString(String key) {
        return getExtraAs(key, FunctionKit.TO_STRING);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f,\u5e76\u8f6c\u6362\u4e3a int \u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @return \u8d26\u53f7id
     *
     * @param key \u6269\u5c55\u4fe1\u606f\u952e
     */
    public static Integer getExtraAsInteger(String key) {
        return getExtraAs(key, FunctionKit.TO_INTEGER);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u8f6c\u6362\u4e3a long \u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @return \u8d26\u53f7id
     *
     * @param key \u6269\u5c55\u4fe1\u606f\u952e
     */
    public static Long getExtraAsLong(String key) {
        return getExtraAs(key, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过自定义函数转换为指定类型（此函数只在jwt模式下生效）
     *
     * @param key    缓存key
     * @param mapper 对象转换函数
     * @param <T>    指定的类型
     * @return 转换函数转换后的对象
     */
    public static <T> T getExtraAs(String key, Function<Object, T> mapper) {
        Object obj = StpUtil.getExtra(key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u901a\u8fc7 Jackson \u8f6c\u6362\u4e3a\u6307\u5b9a\u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @param key \u952e\u503c
     * @param valueType \u8f6c\u6362\u7c7b\u578b
     * @return \u5bf9\u5e94\u7684\u6269\u5c55\u6570\u636e
     *
     * @param <T> \u6269\u5c55\u6570\u636e\u7c7b\u578b
     */
    public static <T> T getExtraAs(String key, Class<T> valueType) {
        Object value = StpUtil.getExtra(key);
        return JsonKit.toType(value, valueType);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u8f6c\u6362\u4e3a long \u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @return \u8d26\u53f7id
     *
     * @param tokenValue \u6307\u5b9a\u7684 Token \u503c
     * @param key \u6269\u5c55\u4fe1\u606f\u952e
     */
    public static Long getExtraAsLong(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_LONG);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u901a\u8fc7\u81ea\u5b9a\u4e49\u51fd\u6570\u8f6c\u6362\u4e3a\u6307\u5b9a\u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @param key \u7f13\u5b58key
     * @param mapper \u5bf9\u8c61\u8f6c\u6362\u51fd\u6570
     * @param <T> \u6307\u5b9a\u7684\u7c7b\u578b
     * @return \u8f6c\u6362\u51fd\u6570\u8f6c\u6362\u540e\u7684\u5bf9\u8c61
     *
     * @param tokenValue \u6307\u5b9a\u7684 Token \u503c
     */
    public static <T> T getExtraAs(String tokenValue, String key, Function<Object, T> mapper) {
        Object obj = StpUtil.getExtra(tokenValue, key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u901a\u8fc7 Jackson \u8f6c\u6362\u4e3a\u6307\u5b9a\u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @param tokenValue \u6307\u5b9a\u7684 Token \u503c
     * @param key \u952e\u503c
     * @param valueType \u8f6c\u6362\u7c7b\u578b
     * @return \u5bf9\u5e94\u7684\u6269\u5c55\u6570\u636e
     *
     * @param <T> \u6269\u5c55\u6570\u636e\u7c7b\u578b
     */
    public static <T> T getExtraAs(String tokenValue, String key, Class<T> valueType) {
        Object value = StpUtil.getExtra(tokenValue, key);
        return JsonKit.toType(value, valueType);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f, \u5e76\u8f6c\u6362\u4e3a String \u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @return \u8d26\u53f7id
     *
     * @param tokenValue \u6307\u5b9a\u7684 Token \u503c
     * @param key \u6269\u5c55\u4fe1\u606f\u952e
     */
    public String getExtraAsString(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_STRING);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Token \u7684\u6269\u5c55\u4fe1\u606f,\u5e76\u8f6c\u6362\u4e3a int \u7c7b\u578b\uff08\u6b64\u51fd\u6570\u53ea\u5728jwt\u6a21\u5f0f\u4e0b\u751f\u6548\uff09
     * @return \u8d26\u53f7id
     *
     * @param tokenValue \u6307\u5b9a\u7684 Token \u503c
     * @param key \u6269\u5c55\u4fe1\u606f\u952e
     */
    public Integer getExtraAsInteger(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_INTEGER);
    }

}