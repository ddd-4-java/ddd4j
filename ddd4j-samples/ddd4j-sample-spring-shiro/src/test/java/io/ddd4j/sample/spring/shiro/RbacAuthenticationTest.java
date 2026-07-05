package io.ddd4j.sample.spring.shiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthenticationController 鉴权测试（Shiro）。
 *
 * <p>Shiro 与 Sa-Token 的区别：注解用 {@code @RequiresAuthentication} /
 * {@code @RequiresRoles} / {@code @RequiresPermissions}。鉴权行为一致。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("鉴权测试 - Shiro")
class RbacAuthenticationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String login(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            return body.path("token").asText(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String adminToken() { return login("admin", "123456"); }
    private String userToken() { return login("user", "123456"); }
    private String managerToken() { return login("manager", "123456"); }

    // ============================ 1) 登录鉴权 ============================

    @Test
    @DisplayName("登录 - 合法凭证 → success=true")
    void login_validCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("登录 - 错误密码 → success=false")
    void login_invalidPassword_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("登录 - 不存在用户 → success=false")
    void login_unknownUser_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"nobody\",\"password\":\"any\"}"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("登录 - 禁用账号 → success=false")
    void login_disabledAccount_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"disabled\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("登出 - 成功")
    void logout_succeeds() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("/auth/me - 未登录 → authenticated=false")
    void me_notLoggedIn_returnsFalse() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("/auth/me - 已登录 → authenticated=true")
    void me_loggedIn_returnsPrincipal() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/auth/me").header("ddd4j-token", token))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("10001"));
    }

    @Test
    @DisplayName("/auth/status - 状态查询")
    void status_returnsLoginFlag() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(jsonPath("$.login").exists());
    }

    @Test
    @DisplayName("踢人下线")
    void kickout_returnsUserId() throws Exception {
        mockMvc.perform(post("/auth/kickout")
                        .contentType("application/json")
                        .content("{\"userId\":\"10002\"}"))
                .andExpect(jsonPath("$.kicked").value("10002"));
    }

    // ============================ 2) 角色鉴权 ============================

    @Test
    @DisplayName("编程式角色检查 - admin → admin 角色 true")
    void checkRole_adminUser_adminRole_returnsTrue() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/auth/check/role")
                        .header("ddd4j-token", token)
                        .contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("编程式角色检查 - user → admin 角色 false")
    void checkRole_normalUser_adminRole_returnsFalse() throws Exception {
        String token = userToken();
        mockMvc.perform(post("/auth/check/role")
                        .header("ddd4j-token", token)
                        .contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(jsonPath("$.has").value(false));
    }

    @Test
    @DisplayName("/auth/check/role 未登录 → 拦截")
    void checkRole_unauthenticated_blocked() throws Exception {
        mockMvc.perform(post("/auth/check/role")
                        .contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().is(unauthorizedOrForbidden()));
    }

    @Test
    @DisplayName("/auth/admin - admin 用户 200")
    void adminEndpoint_adminUser_succeeds() throws Exception {
        mockMvc.perform(get("/auth/admin").header("ddd4j-token", adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/admin - user 用户被拒绝")
    void adminEndpoint_normalUser_forbidden() throws Exception {
        mockMvc.perform(get("/auth/admin").header("ddd4j-token", userToken()))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    @Test
    @DisplayName("/auth/manager - manager 角色 200")
    void managerEndpoint_managerUser_succeeds() throws Exception {
        mockMvc.perform(get("/auth/manager").header("ddd4j-token", managerToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/manager - admin 角色被拒绝")
    void managerEndpoint_adminUser_forbidden() throws Exception {
        mockMvc.perform(get("/auth/manager").header("ddd4j-token", adminToken()))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    // ============================ 3) 权限鉴权 ============================

    @Test
    @DisplayName("编程式权限检查 - admin → user:list true")
    void checkPermission_adminUser_userListPermission_returnsTrue() throws Exception {
        mockMvc.perform(post("/auth/check/permission")
                        .header("ddd4j-token", adminToken())
                        .contentType("application/json")
                        .content("{\"permission\":\"user:list\"}"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("编程式权限检查 - user → user:add false")
    void checkPermission_userLacksPermission_returnsFalse() throws Exception {
        mockMvc.perform(post("/auth/check/permission")
                        .header("ddd4j-token", userToken())
                        .contentType("application/json")
                        .content("{\"permission\":\"user:add\"}"))
                .andExpect(jsonPath("$.has").value(false));
    }

    @Test
    @DisplayName("/auth/users - 拥有 user:list 权限 200")
    void usersEndpoint_adminUser_succeeds() throws Exception {
        mockMvc.perform(get("/auth/users").header("ddd4j-token", adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/users - 普通 user 有 user:list → 200")
    void usersEndpoint_normalUser_succeeds() throws Exception {
        mockMvc.perform(get("/auth/users").header("ddd4j-token", userToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/orders/{id}/pay - 有 order:pay 200")
    void payOrder_withPermission_succeeds() throws Exception {
        mockMvc.perform(post("/auth/orders/order-1/pay").header("ddd4j-token", adminToken()))
                .andExpect(jsonPath("$.message").value("order paid"));
    }

    @Test
    @DisplayName("/auth/orders/{id}/pay - user 没有 order:pay")
    void payOrder_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(post("/auth/orders/order-1/pay").header("ddd4j-token", userToken()))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    // ============================ 4) 组合鉴权 ============================

    @Test
    @DisplayName("DELETE /auth/users/{id} - admin + user:delete 组合 → 200")
    void deleteUser_adminRoleAndPermission_succeeds() throws Exception {
        mockMvc.perform(delete("/auth/users/99999").header("ddd4j-token", adminToken()))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /auth/users/{id} - 非 admin")
    void deleteUser_normalUser_forbidden() throws Exception {
        mockMvc.perform(delete("/auth/users/99999").header("ddd4j-token", userToken()))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    private static org.hamcrest.Matcher<Integer> forbiddenOrUnauthorized() {
        return org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(403),
                org.hamcrest.Matchers.is(401));
    }

    private static org.hamcrest.Matcher<Integer> unauthorizedOrForbidden() {
        return forbiddenOrUnauthorized();
    }
}
