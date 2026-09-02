package com.example.materialpull.security;

import com.example.materialpull.enums.UserRole;
import java.security.Principal;
import java.util.List;

public record ScopePrincipal(Long userId, String name, UserRole role, String factory,
                             List<String> deliveryAreas) implements Principal {
    public ScopePrincipal {
        deliveryAreas = deliveryAreas == null ? List.of() : List.copyOf(deliveryAreas);
    }
    @Override public String getName() { return name; }
    public boolean globalAdmin() {
        return role == UserRole.ADMIN && (factory == null || factory.isBlank()) && deliveryAreas.isEmpty();
    }
}
