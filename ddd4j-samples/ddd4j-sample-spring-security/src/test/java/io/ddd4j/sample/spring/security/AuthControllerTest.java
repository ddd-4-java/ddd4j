package io.ddd4j.sample.spring.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 集成测试（Spring Boot + Spring Security）。
 *
 * <p>使用 HTTP Basic + Spring Security 演示：
 * <ul>
 *   <li>登录（form param）</li>
 *   <li>当前用户 / 登录状态</li>
 *   <li>角色 / 权限 / 组合鉴权（{@code @PreAuthorize}）</li>
 *   <li>业务接口鉴权（{@code /auth/orders/{id}/pay}）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:admin123".getBytes());
    private static final String ALICE_BASIC = "Basic " + Base64.getEncoder().encodeToString("alice:alice123".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLoginSuccessfully() throws Exception {
        mockMvc.perform(post("/auth/login").param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.principal.userId").value("10001"));
    }

    @Test
    void shouldReturnNotAuthenticatedWhenNotLogin() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void shouldReturnLoginStatus() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(false));
    }

    @Test
    void shouldListPermissionsAsAdmin() throws Exception {
        mockMvc.perform(get("/auth/permissions").header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk());
    }

    @Test
    void shouldListRolesAsAdmin() throws Exception {
        mockMvc.perform(get("/auth/roles").header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk());
    }

    @Test
    void shouldListUsersAsAdmin() throws Exception {
        mockMvc.perform(get("/auth/users").header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowListUsersForUserWithPermission() throws Exception {
        mockMvc.perform(get("/auth/users").header("Authorization", ALICE_BASIC))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowBusinessPayOrderForAdmin() throws Exception {
        mockMvc.perform(post("/auth/orders/o-001/pay").header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paid"));
    }

    @Test
    void shouldDenyBusinessPayOrderForRegularUser() throws Exception {
        // alice 仅有 user:list + order:list 权限，没有 order:pay
        mockMvc.perform(post("/auth/orders/o-001/pay").header("Authorization", ALICE_BASIC))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowDeleteUserForAdmin() throws Exception {
        mockMvc.perform(delete("/auth/users/99999").header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyDeleteUserForRegularUser() throws Exception {
        mockMvc.perform(delete("/auth/users/99999").header("Authorization", ALICE_BASIC))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCheckPermissionWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/auth/check/permission")
                        .param("permission", "user:list")
                        .header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("user:list"));
    }

    @Test
    void shouldCheckRoleWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/auth/check/role")
                        .param("role", "admin")
                        .header("Authorization", ADMIN_BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));
    }

}
