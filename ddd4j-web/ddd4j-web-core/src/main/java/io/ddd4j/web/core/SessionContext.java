package io.ddd4j.web.core;

import lombok.Data;

import java.io.Serializable;

@Data
public class SessionContext implements Serializable {
    // 所属租户
    private String tenantId;
    // 微信关联用户ID
    private String wxUserId;
    // 配置项ID
    private String appId;
    // 微信sessionKey
    private String sessionKey;
    // 用户标识
    private String openId;
    // 商城关联用户ID
    private String userId;
    // 是否是企业用户
    private boolean isEnterprise;
    // 是否是企业管理员
    private boolean isEnterpriseAdmin;
    // 企业会员ID：当PC端登陆后，切换了企业身份账户时候，请求过来会替换掉登陆账户的ID，当前PC端登陆的账户ID就是这个
    private String enterpriseUserId;
}