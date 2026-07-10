package io.ddd4j.sample.spring.satoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthenticationController 鉴权测试（Sa-Token）。
 *
 * <p>覆盖 3 种鉴权场景：登录鉴权、角色鉴权、权限鉴权、组合鉴权。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("鉴权测试 - Sa-Token")
class RbacAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static org.hamcrest.Matcher<Integer> forbiddenOrUnauthorized() {
        return org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(403),
                org.hamcrest.Matchers.is(401));
    }

    private static org.hamcrest.Matcher<Integer> unauthorizedOrForbidden() {
        return forbiddenOrUnauthorized();
    }

    /**
     * 登录并返回 token。
     */
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

    private String adminToken() {
        return login("admin", "123456");
    }

    // ============================ 1) 登录鉴权 ============================

    private String userToken() {
        return login("user", "123456");
    }

    private String managerToken() {
        return login("manager", "123456");
    }

    @Test
    @DisplayName("登录 - 合法凭证 → success=true + token")
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
                        .content("{\"username\":\"admin\",\"password\":\"wrong-pwd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("登录 - 不存在的用户 → success=false")
    void login_unknownUser_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"nobody\",\"password\":\"any\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("登录 - 禁用账号 → success=false")
    void login_disabledAccount_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"disabled\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("登出 - 成功")
    void logout_succeeds() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("/auth/me - 未登录返回 authenticated=false")
    void me_notLoggedIn_returnsFalse() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("/auth/me - 已登录返回 principal 信息")
    void me_loggedIn_returnsPrincipal() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/auth/me").header("ddd4j-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("10001"));
    }

    // ============================ 2) 角色鉴权 ============================

    @Test
    @DisplayName("/auth/status - 查询当前登录状态")
    void status_returnsLoginFlag() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").exists());
    }

    @Test
    @DisplayName("踢人下线 - 返回被踢用户 ID")
    void kickout_returnsUserId() throws Exception {
        mockMvc.perform(post("/auth/kickout")
                        .contentType("application/json")
                        .content("{\"userId\":\"10002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kicked").value("10002"));
    }

    @Test
    @DisplayName("编程式角色检查 - admin 拥有 admin 角色 → has=true")
    void checkRole_adminUser_adminRole_returnsTrue() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/auth/check/role")
                        .header("ddd4j-token", token)
                        .contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("编程式角色检查 - user 角色用户 → has=false")
    void checkRole_normalUser_adminRole_returnsFalse() throws Exception {
        String token = userToken();
        mockMvc.perform(post("/auth/check/role")
                        .header("ddd4j-token", token)
                        .contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has").value(false));
    }

    @Test
    @DisplayName("/auth/check/role - 未登录被拦截（401/403）")
    void checkRole_unauthenticated_blocked() throws Exception {
        mockMvc.perform(post("/auth/check/role")
                        .contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().is(unauthorizedOrForbidden()));
    }

    @Test
    @DisplayName("/auth/admin - admin 用户可访问")
    void adminEndpoint_adminUser_succeeds() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/auth/admin").header("ddd4j-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin area accessed"));
    }

    @Test
    @DisplayName("/auth/admin - user 角色用户被拒绝（403）")
    void adminEndpoint_normalUser_forbidden() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/auth/admin").header("ddd4j-token", token))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    // ============================ 3) 权限鉴权 ============================

    @Test
    @DisplayName("/auth/manager - manager 用户可访问")
    void managerEndpoint_managerUser_succeeds() throws Exception {
        String token = managerToken();
        mockMvc.perform(get("/auth/manager").header("ddd4j-token", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/manager - admin 用户被拒绝（admin 无 manager 角色）")
    void managerEndpoint_adminUser_forbidden() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/auth/manager").header("ddd4j-token", token))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    @Test
    @DisplayName("编程式权限检查 - admin 拥有 user:list 权限 → has=true")
    void checkPermission_adminUser_userListPermission_returnsTrue() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/auth/check/permission")
                        .header("ddd4j-token", token)
                        .contentType("application/json")
                        .content("{\"permission\":\"user:list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("user:list"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("编程式权限检查 - user 缺少 user:add 权限 → has=false")
    void checkPermission_userLacksPermission_returnsFalse() throws Exception {
        String token = userToken();
        mockMvc.perform(post("/auth/check/permission")
                        .header("ddd4j-token", token)
                        .contentType("application/json")
                        .content("{\"permission\":\"user:add\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has").value(false));
    }

    @Test
    @DisplayName("/auth/users - admin 有 user:list 权限")
    void usersEndpoint_adminUser_succeeds() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/auth/users").header("ddd4j-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("user list accessed with permission"));
    }

    @Test
    @DisplayName("/auth/users - user 有 user:list 权限 → 200")
    void usersEndpoint_normalUser_succeeds() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/auth/users").header("ddd4j-token", token))
                .andExpect(status().isOk());
    }

    // ============================ 4) 组合鉴权 ============================

    @Test
    @DisplayName("/auth/orders/{id}/pay - 有 order:pay 权限 → 200")
    void payOrder_withPermission_succeeds() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/auth/orders/order-1/pay").header("ddd4j-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("order paid"));
    }

    @Test
    @DisplayName("/auth/orders/{id}/pay - user 没有 order:pay 权限 → 403")
    void payOrder_withoutPermission_forbidden() throws Exception {
        String token = userToken();
        mockMvc.perform(post("/auth/orders/order-1/pay").header("ddd4j-token", token))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }

    // ============================ 辅助 ============================

    @Test
    @DisplayName("DELETE /auth/users/{id} - admin + user:delete → 200")
    void deleteUser_adminRoleAndPermission_succeeds() throws Exception {
        String token = adminToken();
        mockMvc.perform(delete("/auth/users/99999").header("ddd4j-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /auth/users/{id} - 非 admin（即便有权限） → 403")
    void deleteUser_normalUser_forbidden() throws Exception {
        String token = userToken();
        mockMvc.perform(delete("/auth/users/99999").header("ddd4j-token", token))
                .andExpect(status().is(forbiddenOrUnauthorized()));
    }
}
