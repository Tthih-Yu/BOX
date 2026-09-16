package com.example.materialpull.controller;

import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.security.RequireRoles;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingPermissionTest {

    @Test
    void mappingManagementEndpointsAreRestrictedToAdminAndSubAdmin() throws Exception {
        assertAdminRoles(BasicDataController.class.getMethod("saveMapping", MaterialMappingEntity.class));
        assertAdminRoles(BasicDataController.class.getMethod("delMapping", Long.class));
        assertAdminRoles(BasicDataController.class.getMethod("exportMappings"));
        assertAdminRoles(BasicDataController.class.getMethod(
                "deleteMappings", BasicDataController.MappingDeleteRequest.class));
    }

    private void assertAdminRoles(Method method) {
        RequireRoles annotation = method.getAnnotation(RequireRoles.class);
        assertEquals(Set.of(UserRole.ADMIN, UserRole.SUB_ADMIN),
                Set.copyOf(Arrays.asList(annotation.value())), method.getName());
    }
}
