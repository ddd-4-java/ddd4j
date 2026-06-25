package io.ddd4j.boot.core.subject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.*;

@Accessors(chain = true)
@Data
public class AuthPrincipal implements Serializable {

    //==============================认证授权信息====================================

    /**
     * 所属组织ID
     */
    private Object orgId;
    /**
     * 用户 OpenId
     * 说明：openid 是用户在某一 client 下的唯一标识，其有如下特点：
     * - 一个用户在同一个 client 下，openid 是固定的，每次请求都会返回相同的值。
     * - 一个用户在不同的 client 下，openid 是不同的，会返回不同的值。
     */
    private String openid;
    /**
     * 用户 UnionId
     * 说明：UnionId 的特点与 OpenId 几乎一致：同一用户在不同 client 里的 UnionId 值是不同的，除非这些应用属于同一主体。
     * 例如：甲公司申请了应用A、应用B、应用C，乙公司申请了应用D、应用F，那么用户张三：
     * - 在应用 A、B、C 里的 UnionId 值一致。
     * - 在应用 D、F 里的 UnionId 值一致。
     * - 在应用 A 和 应用 D 之间，UnionId 值不一致。
     */
    private String unionId;
    /**
     * 登录账号ID（账号来源表Id）
     */
    private Object loginId;
    /**
     * 用户ID（用户来源表Id）
     */
    private Object userId;
    /**
     * 用户Code（内部工号）
     */
    private String userCode;
    /**
     * 用户类型（可用于区分用户业务）
     */
    private String userType;
    /**
     * 角色ID（角色表Id）
     */
    private Object roleId;
    /**
     * 角色Code：角色业务表中的唯一编码
     */
    private String roleCode;
    /**
     * 用户拥有角色列表
     */
    private List<RolePair> roles;
    /**
     * 用户权限标记列表
     */
    private Set<String> perms = new HashSet<>();
    /**
     * 用户数据
     */
    private Map<String, Object> profile = new HashMap<String, Object>();

    //==============================辅助信息====================================

    /**
     * 用户是否绑定信息
     */
    private boolean bound = Boolean.FALSE;
    /**
     * 用户是否完善信息
     */
    private boolean initial = Boolean.FALSE;
    /**
     * 用户是否需要多因子验证
     */
    private boolean verify = Boolean.FALSE;

    //==============================此次登录的请求来源====================================

    /**
     * 此次登录的客户端ID
     */
    private String appId;
    /**
     * 此次登录的客户端渠道编码
     */
    private String appChannel;
    /**
     * 此次登录的客户端版本号
     */
    private String appVersion;
    /**
     * 此次登录的请求来源IP地址
     */
    private String ipAddress;
    /**
     * 此次登录的客户端设备类型
     */
    private String deviceType;
    /**
     * 此次登录的客户端设备id
     */
    private String deviceId;
    /**
     * 此次登录的客户端 UserAgent 信息
     */
    private String userAgent;

    @Accessors(chain = true)
    @Data
    public static class RolePair implements Serializable {

        /**
         * 角色Id
         */
        private String roleId;
        /**
         * 角色唯一编码
         */
        private String roleCode;
        /**
         * 角色名称
         */
        private String roleName;
        /**
         * 角色是否需要多因子验证
         */
        private boolean verify = Boolean.FALSE;

    }
}
