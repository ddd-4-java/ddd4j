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
package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.stp.StpInterface;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.util.SubjectKit;

import java.util.List;
import java.util.Objects;

/**
 * Bridges Sa-Token annotation authorization to ddd4j's SubjectDataProvider.
 */
public class SaTokenSubjectDataBridge implements StpInterface {

    /**
     * 读取指定认证域中的账号主体并查询业务权限。
     *
     * @param loginId   当前认证域内的账号标识
     * @param loginType 所属的 Sa-Token 认证域
     * @return 账号权限列表，找不到主体时返回空列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        AuthPrincipal principal = SubjectKit.getSubject(loginType).getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return List.of();
        }
        return SubjectKit.getDataProvider().getPermissionList(principal);
    }

    /**
     * 读取指定认证域中的账号主体并查询业务角色。
     *
     * @param loginId   当前认证域内的账号标识
     * @param loginType 所属的 Sa-Token 认证域
     * @return 账号角色列表，找不到主体时返回空列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AuthPrincipal principal = SubjectKit.getSubject(loginType).getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return List.of();
        }
        return SubjectKit.getDataProvider().getRoleList(principal);
    }
}
