package com.achintha.storeservice.support;

import com.achintha.storeservice.security.Role;
import java.util.List;
import java.util.UUID;

/** A user as user-service would describe it in a token. */
public record TestUser(UUID id, String publicId, Role role, UUID storeId, List<String> perms, long tokenVersion) {

    public TestUser withTokenVersion(long tv) {
        return new TestUser(id, publicId, role, storeId, perms, tv);
    }
}
