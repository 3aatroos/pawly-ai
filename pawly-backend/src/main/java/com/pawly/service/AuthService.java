package com.pawly.service;

import com.pawly.dto.AuthResponse;
import com.pawly.dto.LoginRequest;
import com.pawly.dto.RegisterRequest;
import com.pawly.entity.User;
import com.pawly.entity.VetProfile;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.UserRepository;
import com.pawly.repository.VetProfileRepository;
import com.pawly.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VetProfileRepository vetProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + req.getEmail());
        }

        User.UserStatus initialStatus = req.getRole() == User.Role.VETERINARIAN
                ? User.UserStatus.PENDING
                : User.UserStatus.ACTIVE;

        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .phone(req.getPhone())
                .role(req.getRole() != null ? req.getRole() : User.Role.PET_OWNER)
                .status(initialStatus)
                .build();

        user = userRepository.save(user);

        // Automatically create empty vet profile for veterinarians
        if (user.getRole() == User.Role.VETERINARIAN) {
            VetProfile vp = VetProfile.builder()
                    .user(user)
                    .specialization("General Practice")
                    .verificationStatus(VetProfile.VerificationStatus.PENDING)
                    .build();
            vetProfileRepository.save(vp);
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildAuthResponse(token, user);
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", 0L));

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildAuthResponse(token, user);
    }

    private AuthResponse buildAuthResponse(String token, User user) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
