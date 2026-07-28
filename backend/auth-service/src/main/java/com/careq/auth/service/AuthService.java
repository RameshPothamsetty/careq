package com.careq.auth.service;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.LoginRequestDto;
import com.careq.auth.dto.SignupRequestDto;

public interface AuthService {

    AuthResponseDto signup(SignupRequestDto request);

    AuthResponseDto login(LoginRequestDto request);
}
