/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.auth.satoken.util;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.auth.satoken.SaConstants;
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
    public static final StpLogic ADMIN = SaManager.getStpLogic("admin");

    /**
     * User 会话对象，管理 User 表所有账号的登录、权限认证
     */
    public static final StpLogic USER = SaManager.getStpLogic("user");

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
        return getIdClaim(SaConstants.PAYLOAD_USER_ID, "userId");
    }

    /**
     * 获取当前会话的用户 ID（long 类型）。
     *
     * @return 用户 ID
     */
    public static Long getUserIdAsLong() {
        return FunctionKit.TO_LONG.apply(getUserId());
    }

    /**
     * 获取当前会话的用户 ID（String 类型）。
     *
     * @return 用户 ID
     */
    public static String getUserIdAsString() {
        return FunctionKit.TO_STRING.apply(getUserId());
    }

    /**
     * 获取当前会话的用户 ID（Integer 类型）。
     *
     * @return 用户 ID
     */
    public static Integer getUserIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getUserId());
    }

    /**
     * 获取当前会话的机构/组织 ID。
     *
     * @return 机构 ID
     */
    public static Object getOrgId() {
        return getIdClaim(SaConstants.PAYLOAD_ORG_ID, "orgId");
    }

    /**
     * 获取当前会话的机构/组织 ID（String 类型）。
     *
     * @return 机构 ID
     */
    public static String getOrgIdAsString() {
        return FunctionKit.TO_STRING.apply(getOrgId());
    }

    /**
     * 获取当前会话的机构/组织 ID（Integer 类型）。
     *
     * @return 机构 ID
     */
    public static Integer getOrgIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getOrgId());
    }

    /**
     * 获取当前会话的机构/组织 ID（Long 类型）。
     *
     * @return 机构 ID
     */
    public static Long getOrgIdAsLong() {
        return FunctionKit.TO_LONG.apply(getOrgId());
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
     * 以 Long 读取旧版信息条目访问器约定的 JWT 机构字段 {@code org_id}。
     *
     * @return 机构 ID，载荷中缺失时返回 null
     * @throws NumberFormatException 字段值无法转换为 Long 时抛出
     */
    public static Long getInfoIdAsLong() {
        return getExtraAs(SaConstants.PAYLOAD_ORG_ID, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前会话的信息条目 ID（String 类型）。
     *
     * @return 信息条目 ID，载荷中缺失时返回 null
     */
    public static String getInfoIdAsString() {
        return getExtraAs(SaConstants.PAYLOAD_INFO_ID, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前会话的信息条目 ID（Integer 类型）。
     *
     * @return 信息条目 ID，载荷中缺失时返回 null
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
        return getIdClaim(SaConstants.PAYLOAD_ROLE_ID, "roleId");
    }

    /**
     * 获取当前会话的角色 ID（String 类型）。
     *
     * @return 角色 ID
     */
    public static String getRoleIdAsString() {
        return FunctionKit.TO_STRING.apply(getRoleId());
    }

    /**
     * 获取当前会话的角色 ID（Integer 类型）。
     *
     * @return 角色 ID
     */
    public static Integer getRoleIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getRoleId());
    }

    /**
     * 获取当前会话的角色 ID（Long 类型）。
     *
     * @return 角色 ID
     */
    public static Long getRoleIdAsLong() {
        return FunctionKit.TO_LONG.apply(getRoleId());
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

    private static Object getIdClaim(String claimKey, String fieldKey) {
        Object value = StpUtil.getExtra(claimKey);
        return Objects.nonNull(value) ? value : StpUtil.getExtra(fieldKey);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 String 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     * @param key 扩展信息键
     */
    public static String getExtraAsString(String key) {
        return getExtraAs(key, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前 Token 的扩展信息,并转换为 int 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     * @param key 扩展信息键
     */
    public static Integer getExtraAsInteger(String key) {
        return getExtraAs(key, FunctionKit.TO_INTEGER);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 long 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     * @param key 扩展信息键
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
     * 获取当前 Token 的扩展信息, 并通过 Jackson 转换为指定类型（此函数只在jwt模式下生效）
     *
     * @param key       键值
     * @param valueType 转换类型
     * @return 对应的扩展数据
     * @param <T> 扩展数据类型
     */
    public static <T> T getExtraAs(String key, Class<T> valueType) {
        Object value = StpUtil.getExtra(key);
        return JsonKit.toType(value, valueType);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 long 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     * @param tokenValue 指定的 Token 值
     * @param key 扩展信息键
     */
    public static Long getExtraAsLong(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_LONG);
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过自定义函数转换为指定类型（此函数只在jwt模式下生效）
     *
     * @param key    缓存key
     * @param mapper 对象转换函数
     * @param <T>    指定的类型
     * @return 转换函数转换后的对象
     * @param tokenValue 指定的 Token 值
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
     * @param <T> 扩展数据类型
     */
    public static <T> T getExtraAs(String tokenValue, String key, Class<T> valueType) {
        Object value = StpUtil.getExtra(tokenValue, key);
        return JsonKit.toType(value, valueType);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 String 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     * @param tokenValue 指定的 Token 值
     * @param key 扩展信息键
     */
    public String getExtraAsString(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_STRING);
    }

    /**
     * 获取当前 Token 的扩展信息,并转换为 int 类型（此函数只在jwt模式下生效）
     *
     * @return 账号id
     * @param tokenValue 指定的 Token 值
     * @param key 扩展信息键
     */
    public Integer getExtraAsInteger(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_INTEGER);
    }

}
