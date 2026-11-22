package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.config.SecurityConfigAccess;
import com.visionmapping.entity.AppUser;
import com.visionmapping.repository.AppUserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V10 (SEC-04-02): passwords are hashed at the configured BCrypt cost, and a
 * hash made at a lower cost is replaced at the next successful sign-in. The
 * test profile uses cost 5 (production 12), so the "old" hash here is cost 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordHashUpgradeFlowTests {

    private static final String PASSWORD = "Quiet-River-Stone-58";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void newHashesUseTheConfiguredCost() throws Exception {
        String email = register();

        assertThat(hashOf(email)).startsWith("$2a$05$");
        assertThat(passwordEncoder.encode("anything")).startsWith("$2a$05$");
    }

    @Test
    void anOldLowCostHashIsUpgradedAtTheNextSignIn() throws Exception {
        String email = register();
        setHash(email, new BCryptPasswordEncoder(4).encode(PASSWORD));

        login(email, PASSWORD).andExpect(status().isOk());

        String upgraded = hashOf(email);
        assertThat(upgraded).startsWith("$2a$05$");
        assertThat(passwordEncoder.matches(PASSWORD, upgraded)).isTrue();
        // The upgraded hash keeps working.
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aWrongPasswordDoesNotTouchTheHash() throws Exception {
        String email = register();
        String old = new BCryptPasswordEncoder(4).encode(PASSWORD);
        setHash(email, old);

        login(email, "Wrong-Password-Here-1").andExpect(status().isUnauthorized());

        assertThat(hashOf(email)).isEqualTo(old);
    }

    @Test
    void aHashAtTheConfiguredCostIsLeftAlone() throws Exception {
        String email = register();
        String current = hashOf(email);

        login(email, PASSWORD).andExpect(status().isOk());

        assertThat(hashOf(email)).isEqualTo(current);
    }

    @Test
    void productionRefusesACostBelowTwelve() {
        assertThatThrownBy(() -> SecurityConfigAccess.checkBcryptCost(10, new String[] {"prod"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12");
        assertThatThrownBy(() -> SecurityConfigAccess.checkBcryptCost(10, new String[0]))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfigAccess.checkBcryptCost(3, new String[] {"local"}))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> SecurityConfigAccess.checkBcryptCost(12, new String[] {"prod"})).doesNotThrowAnyException();
        assertThatCode(() -> SecurityConfigAccess.checkBcryptCost(5, new String[] {"test"})).doesNotThrowAnyException();
    }

    private String register() throws Exception {
        String email = "hash-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Hash User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "10.88." + (email.hashCode() & 0xff) + ".1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password, "rememberMe", false))));
    }

    private String hashOf(String email) {
        return appUserRepository.findByEmail(email).map(AppUser::getPasswordHash).orElseThrow();
    }

    private void setHash(String email, String hash) {
        AppUser user = appUserRepository.findByEmail(email).orElseThrow();
        user.setPasswordHash(hash);
        appUserRepository.save(user);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
