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
package io.ddd4j.auth.security.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;

/**
 * Spring Security UserDetails carrying the framework-neutral ddd4j principal.
 */
public class AuthUserDetails extends User {

    private final AuthPrincipal authPrincipal;

    public AuthUserDetails(String username,
                           String password,
                           boolean enabled,
                           Collection<? extends GrantedAuthority> authorities,
                           AuthPrincipal authPrincipal) {
        super(username, password, enabled, true, true, true, authorities);
        this.authPrincipal = Objects.requireNonNull(authPrincipal, "authPrincipal must not be null");
    }

    public AuthPrincipal getAuthPrincipal() {
        return authPrincipal;
    }
}
