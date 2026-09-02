package com.example.materialpull.service;

import com.example.materialpull.common.SecurityProperties;
import com.example.materialpull.entity.UserDeliveryAreaEntity;
import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.UserDeliveryAreaRepository;
import com.example.materialpull.repository.UserRepository;
import com.example.materialpull.service.session.SessionStore;
import com.example.materialpull.service.realtime.WebSocketConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceDataScopeTest {
    @Mock UserRepository userRepository;
    @Mock SessionStore sessionStore;
    @Mock UserDeliveryAreaRepository userDeliveryAreaRepository;
    @Mock WebSocketConnectionRegistry websocketConnections;
    AuthTokenService service;
    UserEntity user;

    @BeforeEach
    void setUp() {
        service = new AuthTokenService(new SecurityProperties(), userRepository, sessionStore, userDeliveryAreaRepository, websocketConnections);
        user = new UserEntity();
        user.setId(7L);
        user.setUsername("planner");
        user.setRealName("计划员");
        user.setRole(UserRole.PLANNER);
        user.setFactory("弋江");
        user.setEnabled(true);
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void deliveryAreaChangeRevokesExistingSession() {
        AuthTokenService.SessionUser session = session(List.of("T26 Floor"));
        when(sessionStore.getSession("token")).thenReturn(Optional.of(session));
        when(userDeliveryAreaRepository.findByUserId(7L)).thenReturn(List.of(area("T26 Rear")));

        assertTrue(service.validate("token").isEmpty());
        verify(sessionStore).removeSession("token");
        verify(sessionStore, never()).saveSession(any(), any());
    }

    @Test
    void equivalentAreaOrderAndCaseKeepsSessionValid() {
        AuthTokenService.SessionUser session = session(List.of("t26 rear", "T26 FLOOR"));
        when(sessionStore.getSession("token")).thenReturn(Optional.of(session));
        when(userDeliveryAreaRepository.findByUserId(7L))
                .thenReturn(List.of(area("T26 Floor"), area("T26 Rear"), area("T26 Floor")));

        Optional<AuthTokenService.SessionUser> validated = service.validate("token");

        assertTrue(validated.isPresent());
        verify(sessionStore, never()).removeSession(anyString());
        verify(sessionStore).saveSession(any(), any());
    }

    @Test
    void legacySessionWithoutScopeVersionIsRevoked() {
        LocalDateTime now = LocalDateTime.now();
        AuthTokenService.SessionUser legacy = new AuthTokenService.SessionUser("legacy", 7L, "planner", "计划员",
                UserRole.PLANNER, now, null, now.plusHours(1), null, null, null);
        when(sessionStore.getSession("legacy")).thenReturn(Optional.of(legacy));

        assertTrue(service.validate("legacy").isEmpty());
        verify(sessionStore).removeSession("legacy");
        verifyNoInteractions(userDeliveryAreaRepository);
    }

    @Test
    void revokeUserSessionsAlsoInvalidatesWebsocketConnections() {
        service.revokeUserSessions(7L);
        verify(sessionStore).removeUserSessions(7L);
        verify(sessionStore).removeUserTickets(7L);
        verify(websocketConnections).invalidateUser(7L);
    }

    private AuthTokenService.SessionUser session(List<String> areas) {
        LocalDateTime now = LocalDateTime.now();
        return new AuthTokenService.SessionUser("token", 7L, "planner", "计划员", UserRole.PLANNER,
                now, null, now.plusHours(1), "弋江", areas);
    }

    private UserDeliveryAreaEntity area(String code) {
        UserDeliveryAreaEntity area = new UserDeliveryAreaEntity();
        area.setUserId(7L);
        area.setDeliveryArea(code);
        return area;
    }
}
