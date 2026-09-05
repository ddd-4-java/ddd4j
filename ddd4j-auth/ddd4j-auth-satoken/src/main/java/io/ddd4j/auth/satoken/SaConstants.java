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

/**
 * Sa-Token 业务字段常量（sa-token 模块专属，不应提升到 core）。
 *
 * <p>仅包含 Sa-Token 特定的业务字段（学校/校区/机构/身份等），这些是 ddd4j-auth-satoken
 * 模块内部约定，**不应**出现在框架无关层。
 *
 * <p>标准 JWT 字段、JWT/Token/Device 等通用字段请见
 * {@link io.ddd4j.core.constant.AuthConstants}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class SaConstants {

    /**
     * JWT 学校/校区代码。
     */
    public static final String PAYLOAD_SCHOOL_CODE = "xxdm";
    /**
     * JWT 学区/校区组织 ID（与 {@link #PAYLOAD_ORG_ID} 区分）。
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
    private SaConstants() {
    }
}