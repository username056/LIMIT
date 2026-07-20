package com.c203.limit.global.security;

import java.security.Principal;
import java.util.Set;

public record AuthenticatedUser(Long id, String accountType, Set<String> roles) implements Principal {
    @Override public String getName() { return accountType + ":" + id; }
}
