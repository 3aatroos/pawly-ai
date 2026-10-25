package com.pawly.service;

import com.pawly.dto.UpdateProfileRequest;
import com.pawly.dto.UserDTO;
import com.pawly.entity.User;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserDTO findById(Long id) {
        return UserDTO.from(getUser(id));
    }

    public UserDTO findByEmail(String email) {
        return UserDTO.from(userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email)));
    }

    @Transactional
    public UserDTO updateProfile(Long id, UpdateProfileRequest req) {
        User user = getUser(id);
        if (req.getFirstName() != null) user.setFirstName(req.getFirstName());
        if (req.getLastName()  != null) user.setLastName(req.getLastName());
        if (req.getPhone()     != null) user.setPhone(req.getPhone());
        if (req.getAddress()   != null) user.setAddress(req.getAddress());
        if (req.getPhotoUrl()  != null) user.setPhotoUrl(req.getPhotoUrl());
        return UserDTO.from(userRepository.save(user));
    }

    public Page<UserDTO> findAll(User.Role role, User.UserStatus status, String search, Pageable pageable) {
        return userRepository.findAllFiltered(role, status, search, pageable).map(UserDTO::from);
    }

    @Transactional
    public UserDTO setStatus(Long id, User.UserStatus status) {
        User user = getUser(id);
        user.setStatus(status);
        return UserDTO.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        userRepository.deleteById(id);
    }

    // ── Stats ─────────────────────────────────────────────────

    public long countByRole(User.Role role) {
        return userRepository.countByRole(role);
    }

    public long countByStatus(User.UserStatus status) {
        return userRepository.countByStatus(status);
    }

    public long countTotal() {
        return userRepository.count();
    }

    // ── Internal ──────────────────────────────────────────────

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
