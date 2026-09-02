package com.example.materialpull.service.realtime;

import com.example.materialpull.config.WebSocketScopeInterceptor;
import java.util.*;

final class ScopeDestinations {
    private ScopeDestinations() {}

    static List<String> forMessage(String topic, Object body) {
        String base = "/topic/" + topic;
        String factory = property(body, "getFactory");
        String area = property(body, "getDeliveryArea");
        List<String> destinations = new ArrayList<>();
        destinations.add(base + "/@global");
        if (factory != null && area != null) destinations.add(base + "/@area/" + enc(factory) + "/" + enc(area));
        else if (factory != null) destinations.add(base + "/@factory/" + enc(factory));
        return destinations;
    }

    static String factory(Object body) { return property(body, "getFactory"); }
    static String area(Object body) { return property(body, "getDeliveryArea"); }
    static String scopeType(Object body) {
        String factory = factory(body), area = area(body);
        return factory == null ? "GLOBAL" : area == null ? "FACTORY_ONLY" : "FACTORY_AREA";
    }
    private static String enc(String value) { return WebSocketScopeInterceptor.encoded(value); }
    private static String property(Object body, String getter) {
        if (body == null) return null;
        try {
            Object value = body.getClass().getMethod(getter).invoke(body);
            return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
        } catch (Exception ignored) { return null; }
    }
}
