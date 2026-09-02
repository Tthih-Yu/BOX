package com.example.materialpull.config;

import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.ScopePrincipal;
import com.example.materialpull.service.realtime.WebSocketConnectionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WebSocketScopeInterceptorTest {
    private final WebSocketScopeInterceptor interceptor = new WebSocketScopeInterceptor(new WebSocketConnectionRegistry());

    @Test
    void scopedUserCanSubscribeOwnArea() {
        ScopePrincipal user = new ScopePrincipal(1L, "u", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
        String destination = "/topic/tasks/@area/" + WebSocketScopeInterceptor.encoded("弋江") + "/"
                + WebSocketScopeInterceptor.encoded("T26 Floor");
        assertDoesNotThrow(() -> interceptor.preSend(subscribe(user, destination), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void scopedUserCannotSubscribeOtherAreaOrGlobal() {
        ScopePrincipal user = new ScopePrincipal(1L, "u", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(subscribe(user, "/topic/tasks/@global"), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void unauthenticatedConnectIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void malformedAdminWithAreasCannotSubscribeGlobalTopic() {
        ScopePrincipal user = new ScopePrincipal(2L, "broken-admin", UserRole.ADMIN, null, List.of("T26 Floor"));

        assertFalse(user.globalAdmin());
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(subscribe(user, "/topic/tasks/@global"),
                        mock(org.springframework.messaging.MessageChannel.class)));
    }

    private org.springframework.messaging.Message<byte[]> subscribe(ScopePrincipal user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(user);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
