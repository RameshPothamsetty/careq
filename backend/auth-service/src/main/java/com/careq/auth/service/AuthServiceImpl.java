package com.careq.auth.service;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.LoginRequestDto;
import com.careq.auth.dto.SignupRequestDto;
import com.careq.auth.entity.Role;
import com.careq.auth.entity.User;
import com.careq.auth.exception.DuplicateEmailException;
import com.careq.auth.exception.InvalidCredentialsException;
import com.careq.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponseDto signup(SignupRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(
                    "An account with email '" + request.getEmail() + "' already exists"
            );
        }

        Role role = Role.valueOf(request.getRole().toUpperCase());

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                role
        );

        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getFullName(), user.getRole());

        return new AuthResponseDto(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.getIsActive()) {
            throw new InvalidCredentialsException("Account is deactivated. Please contact an administrator.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getFullName(), user.getRole());

        return new AuthResponseDto(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
