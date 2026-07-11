package com.example.authservice.dto.signup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request DTO for email‑based sign‑up, containing email and password. */
public record EmailReadUserRequestDto(@NotBlank @Email String email, @NotBlank String password) {}
