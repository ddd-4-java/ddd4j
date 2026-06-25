package io.ddd4j.boot.cmpt.satoken.util;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.boot.cmpt.satoken.SaConstants;
import io.ddd4j.boot.core.util.Functions;

import java.util.Objects;
import java.util.function.Function;

/**
 * StpLogic 门面类，管理项目中所有的 StpLogic 账号体系
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

    public static Object getUserId() {
        return StpUtil.getExtra(SaConstants.PAYLOAD_USER_ID);
    }

    public static Long getUserIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_USER_ID, Functions.TO_LONG);
    }
    public static String getUserIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_USER_ID, Functions.TO_STRING);
    }
    public static Integer getUserIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_USER_ID, Functions.TO_INTEGER);
    }

    public static Object getOrgId() {
        return StpUtil.getExtra(SaConstants.PAYLOAD_ORG_ID);
    }

    public static String getOrgIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_ORG_ID, Functions.TO_STRING);
    }

    public static Integer getOrgIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_ORG_ID, Functions.TO_INTEGER);
    }

    public static Long getOrgIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, Functions.TO_LONG);
    }

    public static String getXqOrgIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_XQ_ORG_ID, Functions.TO_STRING);
    }

    public static Integer getXqOrgIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_XQ_ORG_ID, Functions.TO_INTEGER);
    }

    public static Long getXqOrgIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_XQ_ORG_ID, Functions.TO_LONG);
    }

    public static Long getInfoIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_ORG_ID, Functions.TO_LONG);
    }

    public static String getInfoIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, Functions.TO_STRING);
    }

    public static Integer getInfoIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, Functions.TO_INTEGER);
    }

    public static Object getRoleId() {
        return StpUtil.getExtra(SaConstants.PAYLOAD_INFO_ID);
    }

    public static String getRoleIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_ROLE_ID, Functions.TO_STRING);
    }

    public static Integer getRoleIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_ROLE_ID, Functions.TO_INTEGER);
    }

    public static Long getRoleIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_ROLE_ID, Functions.TO_LONG);
    }

    public static String getXxdmAsString() {
        return getExtraAs(SaConstants.PAYLOAD_SCHOOL_CODE, Functions.TO_STRING);
    }

    public static Integer getIdentityIdAsInteger() {
        return getExtraAs(SaConstants.PAYLOAD_IDENTITY_ID, Functions.TO_INTEGER);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 String 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     */
    public static String getExtraAsString(String key) {
        return getExtraAs(key, Functions.TO_STRING);
    }

    /**
     * 获取当前 Token 的扩展信息,并转换为 int 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     */
    public static Integer getExtraAsInteger(String key) {
        return getExtraAs(key, Functions.TO_INTEGER);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 long 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     */
    public static Long getExtraAsLong(String key) {
        return getExtraAs(key, Functions.TO_LONG);
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
     * 获取当前 Token 的扩展信息, 并通过 Jackson 转换为指定类型（此函数只在jwt模式下生效）
     *
     * @param key       键值
     * @param valueType 转换类型
     * @return 对应的扩展数据
     */
    public static <T> T getExtraAs(String key, Class<T> valueType) {
        Object value = StpUtil.getExtra(key);
        return JacksonKit.toType(value, valueType);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 String 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     */
    public String getExtraAsString(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_STRING);
    }

    /**
     * 获取当前 Token 的扩展信息,并转换为 int 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     */
    public Integer getExtraAsInteger(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_INTEGER);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 long 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     */
    public static Long getExtraAsLong(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_LONG);
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过自定义函数转换为指定类型（此函数只在jwt模式下生效）
     *
     * @param key    缓存key
     * @param mapper 对象转换函数
     * @param <T>    指定的类型
     * @return 转换函数转换后的对象
     */
    public static <T> T getExtraAs(String tokenValue, String key, Function<Object, T> mapper) {
        Object obj = StpUtil.getExtra(tokenValue, key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过 Jackson 转换为指定类型（此函数只在jwt模式下生效）
     *
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @param valueType  转换类型
     * @return 对应的扩展数据
     */
    public static <T> T getExtraAs(String tokenValue, String key, Class<T> valueType) {
        Object value = StpUtil.getExtra(tokenValue, key);
        return JacksonKit.toType(value, valueType);
    }

}