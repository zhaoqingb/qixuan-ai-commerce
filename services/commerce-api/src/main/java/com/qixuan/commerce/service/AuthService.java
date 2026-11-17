package com.qixuan.commerce.service;

import com.qixuan.commerce.common.TokenService;
import com.qixuan.commerce.dto.LoginRequest;
import com.qixuan.commerce.dto.LoginResponse;
import com.qixuan.commerce.dto.RegisterRequest;
import com.qixuan.commerce.dto.RegisterResponse;
import com.qixuan.commerce.entity.AppUser;
import com.qixuan.commerce.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    /**
     * 一个固定的 BCrypt 哈希（明文是 "password"）。
     * 只在"用户不存在"时用它跑一次 matches，把响应耗时拉到和"密码错误"一致，
     * 防止攻击者用响应时间枚举出哪些用户名真实存在。
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(AppUserMapper appUserMapper, PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String username = request.username();
        Long count = appUserMapper
                .selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username));
        if (count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        String hash = passwordEncoder.encode(request.password());

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setStatus("ACTIVE");
        appUserMapper.insert(user);
        return new RegisterResponse(user.getId(), user.getUsername());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserMapper
                .selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, request.username()));
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        if (user.getStatus() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号被禁用");
        }
        String token = tokenService.issue(user.getId());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }
}
