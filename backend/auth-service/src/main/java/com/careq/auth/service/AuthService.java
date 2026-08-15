package com.careq.auth.service;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.LoginRequestDto;
import com.careq.auth.dto.SignupRequestDto;

public interface AuthService {

    AuthResponseDto signup(SignupRequestDto request, String clientIp);

    AuthResponseDto login(LoginRequestDto request, String clientIp);

    AuthResponseDto verifyEmail(String rawToken);

    AuthResponseDto resendVerification(String email, String clientIp);

    AuthResponseDto forgotPassword(String email, String clientIp);

    AuthResponseDto resetPassword(String token, String newPassword);
}
