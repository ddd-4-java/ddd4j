package io.ddd4j.core.api.subject;

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
     */
    private String openid;
    /**
     * 用户 UnionId
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
