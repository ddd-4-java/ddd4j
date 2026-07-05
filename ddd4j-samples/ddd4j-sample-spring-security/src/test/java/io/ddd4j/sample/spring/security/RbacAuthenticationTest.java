package io.ddd4j.sample.spring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 鉴权测试（Spring Security 示例）：登录 / 当前用户 / 权限 / 角色 / 业务鉴权。
 * 使用 HTTP Basic：admin/admin123, alice/alice123。
 *
 * @author Test
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RBAC 鉴权 - Spring Security")
class RbacAuthenticationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    private String loginToken(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("token").asText();
    }

    @Test
    @DisplayName("admin 登录返回 token")
    void admin_login_returnsToken() throws Exception {
        assertThat(loginToken("admin", "admin123")).isNotEmpty();
    }

    @Test
    @DisplayName("alice 登录返回 token")
    void alice_login_returnsToken() throws Exception {
        assertThat(loginToken("alice", "alice123")).isNotEmpty();
    }

    @Test
    @DisplayName("登录失败 - 错误密码")
    void login_badPassword_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .param("username", "admin")
                        .param("password", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("登录失败 - 未知用户")
    void login_unknownUser_fails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .param("username", "ghost")
                        .param("password", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/auth/me - 登录后")
    void me_afterLogin() throws Exception {
        loginToken("admin", "admin123");
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.loginId").value("10001"));
    }

    @Test
    @DisplayName("/auth/me - 未登录")
    void me_noAuth() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("/auth/status - 登录前")
    void status_beforeLogin() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(jsonPath("$.login").value(false));
    }

    @Test
    @DisplayName("/auth/status - 登录后")
    void status_afterLogin() throws Exception {
        loginToken("admin", "admin123");
        mockMvc.perform(get("/auth/status")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.login").value(true));
    }

    @Test
    @DisplayName("check/permission - 未登录 → 401")
    void checkPermission_noAuth() throws Exception {
        mockMvc.perform(get("/auth/check/permission").param("permission", "user:list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("check/permission - admin 有 user:list")
    void checkPermission_admin() throws Exception {
        loginToken("admin", "admin123");
        mockMvc.perform(get("/auth/check/permission")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("permission", "user:list"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("check/permission - admin 有 user:add")
    void checkPermission_adminUserAdd() throws Exception {
        loginToken("admin", "admin123");
        mockMvc.perform(get("/auth/check/permission")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("permission", "user:add"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("check/permission - alice 无 user:add")
    void checkPermission_aliceNoUserAdd() throws Exception {
        loginToken("alice", "alice123");
        mockMvc.perform(get("/auth/check/permission")
                        .header("Authorization", basic("alice", "alice123"))
                        .param("permission", "user:add"))
                .andExpect(jsonPath("$.has").value(false));
    }

    @Test
    @DisplayName("check/role - admin 有 admin")
    void checkRole_admin_hasAdmin() throws Exception {
        loginToken("admin", "admin123");
        mockMvc.perform(get("/auth/check/role")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("role", "admin"))
                .andExpect(jsonPath("$.has").value(true));
    }

    @Test
    @DisplayName("check/role - alice 无 admin")
    void checkRole_alice_noAdmin() throws Exception {
        loginToken("alice", "alice123");
        mockMvc.perform(get("/auth/check/role")
                        .header("Authorization", basic("alice", "alice123"))
                        .param("role", "admin"))
                .andExpect(jsonPath("$.has").value(false));
    }

    @Test
    @DisplayName("业务 payOrder - admin 有 order:pay → 200")
    void payOrder_admin_succeeds() throws Exception {
        loginToken("admin", "admin123");
        mockMvc.perform(post("/auth/orders/order-x/pay")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.status").value("paid"));
    }

    @Test
    @DisplayName("业务 payOrder - alice 无 order:pay → 403")
    void payOrder_alice_forbidden() throws Exception {
        loginToken("alice", "alice123");
        mockMvc.perform(post("/auth/orders/order-x/pay")
                        .header("Authorization", basic("alice", "alice123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("业务 payOrder - 未登录 → 401/403")
    void payOrder_unauthenticated() throws Exception {
        mockMvc.perform(post("/auth/orders/order-x/pay"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    assertThat(s == 401 || s == 403).isTrue();
                });
    }

    @Test
    @DisplayName("登录 + me + 权限校验 + 角色校验组合流")
    void fullAuthFlow() throws Exception {
        String token = loginToken("admin", "admin123");
        assertThat(token).isNotEmpty();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", basic("admin", "admin123")))
                .andExpect(jsonPath("$.authenticated").value(true));

        mockMvc.perform(get("/auth/check/permission")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("permission", "user:list"))
                .andExpect(jsonPath("$.has").value(true));

        mockMvc.perform(get("/auth/check/role")
                        .header("Authorization", basic("admin", "admin123"))
                        .param("role", "admin"))
                .andExpect(jsonPath("$.has").value(true));
    }
}