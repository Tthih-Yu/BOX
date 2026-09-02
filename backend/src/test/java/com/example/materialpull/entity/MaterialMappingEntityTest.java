package com.example.materialpull.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class MaterialMappingEntityTest {

    @Test
    void persistDoesNotGuessMissingDeliveryArea() {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setDeliveryArea("   ");

        mapping.prePersist();

        assertNull(mapping.getDeliveryArea());
    }
}
