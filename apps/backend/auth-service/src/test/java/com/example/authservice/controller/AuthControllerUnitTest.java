package com.example.authservice.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authservice.service.AuthService;
import com.example.commonlib.exception.UserAlreadyExistsException;
import com.example.commonlib.route.ApiRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Pure controller-layer unit tests. No Spring context is loaded; validation is wired in manually so
 * the {@code @Valid} annotation on the request body is honored.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

  @Mock private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AuthController controller = new AuthController(authService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setValidator(new LocalValidatorFactoryBean())
            .build();
  }

  @Test
  void signUp_validRequest_returns201WithLocationAndBody() throws Exception {
    when(authService.registerUser(anyString(), anyString()))
        .thenReturn("11111111-1111-1111-1111-111111111111");

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP)
                .contentType("application/json")
                .content("{\"email\":\"user@example.com\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", "/api/v1/users/11111111-1111-1111-1111-111111111111"))
        .andExpect(jsonPath("$.status").value(201))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value("11111111-1111-1111-1111-111111111111"));
  }

  @Test
  void signUp_trailingSlashRoute_alsoWorks() throws Exception {
    when(authService.registerUser(anyString(), anyString()))
        .thenReturn("22222222-2222-2222-2222-222222222222");

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP_TS)
                .contentType("application/json")
                .content("{\"email\":\"user2@example.com\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void signUp_existingUser_returns409Conflict() throws Exception {
    when(authService.registerUser(anyString(), anyString()))
        .thenThrow(new UserAlreadyExistsException("User already exists"));

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP)
                .contentType("application/json")
                .content("{\"email\":\"dup@example.com\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void signUp_invalidEmail_returns400() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP)
                .contentType("application/json")
                .content("{\"email\":\"not-an-email\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void signUp_blankPassword_returns400() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP)
                .contentType("application/json")
                .content("{\"email\":\"user@example.com\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void signUp_malformedJson_returns400() throws Exception {
    mockMvc
        .perform(post(ApiRoutes.Auth.SIGN_UP).contentType("application/json").content("not-json"))
        .andExpect(status().isBadRequest());
  }
}
