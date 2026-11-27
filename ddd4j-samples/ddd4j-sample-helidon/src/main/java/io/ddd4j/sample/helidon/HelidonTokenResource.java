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

/** \u63d0\u4f9b Helidon \u793a\u4f8b\u7684\u4ee4\u724c\u7b7e\u53d1\u63a5\u53e3\u3002 */
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

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u793a\u4f8b\u7b7e\u53d1\u6216\u8fd4\u56de\u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002 */
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

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(token);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TokenResponse[token=" + token + "]";
        }
    }
}
