package com.cruvex.cubecraftplus.model;

/** {@code status} is CubeCraft's text after the name, e.g. where they are playing; empty when not known yet. */
public record Friend(String name, boolean online, String status) {
}
