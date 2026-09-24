package com.cruvex.cubecraftplus.friends;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** {@code name} as CubeCraft lists it, {@code id} once certain, {@code status} the text after the name or empty. */
public record Friend(String name, @Nullable UUID id, boolean online, String status) {

    public Friend(String name, boolean online, String status) {
        this(name, null, online, status);
    }

    public Friend withStatus(boolean online, String status) {
        return new Friend(name, id, online, status);
    }

    public Friend withId(@Nullable UUID id) {
        return new Friend(name, id, online, status);
    }
}
