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
package io.ddd4j.core.auth.session;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话上下文信息。
 * <p>封装当前会话中的用户身份信息，包括租户、用户ID、微信关联信息及企业身份等。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class SessionContext implements Serializable {
    /**
     * 所属租户
     */
    private String tenantId;
    /**
     * 微信关联用户ID
     */
    private String wxUserId;
    /**
     * 配置项ID
     */
    private String appId;
    /**
     * 微信 sessionKey
     */
    private String sessionKey;
    /**
     * 用户标识（微信 openId）
     */
    private String openId;
    /**
     * 商城关联用户ID
     */
    private String userId;
    /**
     * 是否是企业用户
     */
    private boolean isEnterprise;
    /**
     * 是否是企业管理员
     */
    private boolean isEnterpriseAdmin;
    /**
     * 企业会员ID：当 PC 端登录后，切换了企业身份账户时，请求过来会替换掉登录账户的 ID
     */
    private String enterpriseUserId;
}