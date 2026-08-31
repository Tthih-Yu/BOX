package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.AuthDtos;
import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.entity.UserDeliveryAreaEntity;
import com.example.materialpull.repository.UserRepository;
import com.example.materialpull.repository.UserDeliveryAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final AuthTokenService tokenService;
    private final PasswordService passwordService;
    private final UserDeliveryAreaRepository userDeliveryAreaRepository;

    @Transactional
    public AuthDtos.LoginResult login(AuthDtos.LoginRequest req) {
        if (req == null || req.username == null || req.username.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账号不能为空");
        }
        if (req.password == null || req.password.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        UserEntity user = userRepository.findByUsername(req.username.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        if (!passwordService.matches(req.password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!passwordService.isBcrypt(user.getPasswordHash())) {
            user.setPasswordHash(passwordService.hashExistingPasswordWithoutPolicyCheck(req.password));
            user.setPasswordUpdatedAt(LocalDateTime.now());
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 加载用户的 DataScope 范围信息
        String factory = user.getFactory();
        List<String> deliveryAreas = userDeliveryAreaRepository.findByUserId(user.getId())
                .stream()
                .map(UserDeliveryAreaEntity::getDeliveryArea)
                .collect(Collectors.toList());

        AuthTokenService.SessionUser session = tokenService.issue(user);
        AuthDtos.LoginResult result = new AuthDtos.LoginResult();
        result.token = session.token();
        result.username = user.getUsername();
        result.realName = user.getRealName();
        result.role = user.getRole();
        result.roleLabel = user.getRole() == null ? null : user.getRole().label;
        result.expiresAt = session.expiresAt();
        
        // DataScope 范围字段
        result.factory = factory;
        result.deliveryAreas = deliveryAreas.isEmpty() ? null : deliveryAreas;
        
        return result;
    }

    public AuthDtos.WsTicketResult createWebsocketTicket() {
        AuthTokenService.SessionUser session = new AuthTokenService.SessionUser(
                "", RequestContext.getUserId(), RequestContext.getUsername(), RequestContext.getRealName(), RequestContext.getRole(), LocalDateTime.now(), null, LocalDateTime.now().plusMinutes(1)
        );
        if (session.username() == null || session.role() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        AuthTokenService.WsTicket ticket = tokenService.issueWebsocketTicket(session);
        AuthDtos.WsTicketResult result = new AuthDtos.WsTicketResult();
        result.ticket = ticket.ticket();
        result.expiresAt = ticket.expiresAt();
        return result;
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }
}
