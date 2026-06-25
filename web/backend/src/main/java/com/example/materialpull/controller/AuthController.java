package com.example.materialpull.controller;

import com.example.materialpull.common.ApiResponse;
import com.example.materialpull.dto.AuthDtos;
import com.example.materialpull.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResult> login(@RequestBody AuthDtos.LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7) ? authorization.substring(7).trim() : null;
        authService.logout(token);
        return ApiResponse.ok(null);
    }

    @PostMapping("/ws-ticket")
    public ApiResponse<AuthDtos.WsTicketResult> websocketTicket() {
        return ApiResponse.ok(authService.createWebsocketTicket());
    }
}
