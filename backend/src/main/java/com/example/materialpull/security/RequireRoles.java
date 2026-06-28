package com.example.materialpull.security;

import com.example.materialpull.enums.UserRole;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRoles {
    UserRole[] value();
}
