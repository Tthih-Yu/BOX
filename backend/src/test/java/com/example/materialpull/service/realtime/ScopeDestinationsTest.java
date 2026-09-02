package com.example.materialpull.service.realtime;

import com.example.materialpull.entity.ReplenishmentTaskEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScopeDestinationsTest {
    @Test
    void factoryAreaMessageRoutesOnlyToGlobalAndExactArea() {
        ReplenishmentTaskEntity task = new ReplenishmentTaskEntity();
        task.setFactory("弋江");
        task.setDeliveryArea("T26 Floor");
        var destinations = ScopeDestinations.forMessage("tasks", task);
        assertEquals(2, destinations.size());
        assertTrue(destinations.contains("/topic/tasks/@global"));
        assertTrue(destinations.stream().anyMatch(x -> x.startsWith("/topic/tasks/@area/")));
        assertFalse(destinations.stream().anyMatch(x -> x.contains("@factory")));
    }
}
