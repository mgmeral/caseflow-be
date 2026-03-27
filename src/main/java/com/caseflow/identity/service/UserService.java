package com.caseflow.identity.service;

import com.caseflow.common.exception.UserNotFoundException;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String username, String email, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setIsActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long userId, String email, String fullName) {
        User user = findOrThrow(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        return userRepository.save(user);
    }

    @Transactional
    public void activate(Long userId) {
        User user = findOrThrow(userId);
        user.setIsActive(true);
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(Long userId) {
        User user = findOrThrow(userId);
        user.setIsActive(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return findOrThrow(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    private User findOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
