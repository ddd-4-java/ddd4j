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
package io.ddd4j.auth.satoken;

import io.ddd4j.core.constant.AuthConstants;

/**
 * Sa-Token 通用认证载荷字段。
 *
 * <p>通用载荷键取自 {@link AuthConstants}，同时保留已发布的学校业务载荷键。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class SaConstants {

    /**
     * 租户 ID。
     */
    public static final String PAYLOAD_TENANT_ID = AuthConstants.CLAIM_TENANT_ID;

    /**
     * 应用 ID。
     */
    public static final String PAYLOAD_APP_ID = AuthConstants.FIELD_APP_ID;

    /**
     * 角色 ID。
     */
    public static final String PAYLOAD_ROLE_ID = AuthConstants.CLAIM_ROLE_ID;

    /**
     * 角色编码。
     */
    public static final String PAYLOAD_ROLE_CODE = AuthConstants.FIELD_ROLE_CODE;

    /**
     * 角色类型。
     */
    public static final String PAYLOAD_ROLE_TYPE = AuthConstants.FIELD_ROLE_TYPE;

    /**
     * 用户 ID。
     */
    public static final String PAYLOAD_USER_ID = AuthConstants.CLAIM_USER_ID;

    /**
     * 机构 ID。
     */
    public static final String PAYLOAD_ORG_ID = AuthConstants.CLAIM_ORG_ID;

    /**
     * JWT 学校/校区代码。
     */
    public static final String PAYLOAD_SCHOOL_CODE = "xxdm";
    /**
     * JWT 学区/校区组织 ID（与学校级组织 ID 区分）。
     */
    public static final String PAYLOAD_XQ_ORG_ID = "xq_org_id";
    /**
     * JWT 身份标识 ID。
     */
    public static final String PAYLOAD_IDENTITY_ID = "iden_id";
    /**
     * JWT 信息条目 ID。
     */
    public static final String PAYLOAD_INFO_ID = "info_id";
    /**
     * JWT 父级信息条目 ID。
     */
    public static final String PAYLOAD_PARENT_INFO_ID = "p_info_id";

    /**
     * 限制调用方直接创建 <code>SaConstants</code> 实例。
     */
    private SaConstants() {
    }
}
