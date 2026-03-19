package com.example.demo.config;

import java.security.Principal;

public record WebSocketPrincipal(String name) implements Principal {
    @Override
    public String getName() {
        return name;
    }
}
