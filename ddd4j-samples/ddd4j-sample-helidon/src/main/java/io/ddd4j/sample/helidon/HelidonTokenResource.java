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
package io.ddd4j.sample.helidon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Objects;

/**
 * 提供 Helidon 示例的令牌签发接口。
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class HelidonTokenResource {
    /**
     * @param userId 演示用户标识
     * @return 登录令牌响应
     */
    @POST
    @Path("/auth/tokens/{userId}")
    public R<TokenResponse> issueToken(@PathParam("userId") String userId) {
        return R.ok(new TokenResponse(SubjectKit.getSubject().login(AuthRequest.of(userId))));
    }

    /**
     * 登录令牌响应。
     */
    public final static class TokenResponse {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 当前示例签发或返回客户端的会话令牌。
         */
        private final String token;

        /**
         * @param token 登录令牌
         */
        @JsonCreator()
        public TokenResponse(@JsonProperty("token") String token) {
            this.token = token;
        }

        /**
         * @return 登录令牌
         */
        @JsonProperty("token")
        public String token() {
            return token;
        }

        /**
         * @return 登录令牌，供 JSON-B 按 Bean 属性序列化
         */
        @JsonbProperty("token")
        public String getToken() {
            return token;
        }

        /**
         * 根据<code>token</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TokenResponse other = (TokenResponse) obj;
            return Objects.equals(this.token, other.token);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(token);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>token</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TokenResponse[token=" + token + "]";
        }
    }
}
