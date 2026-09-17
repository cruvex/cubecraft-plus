package com.cruvex.cubecraftplus.model;

/**
 * A friends list entry. {@code status} is CubeCraft's text after the name, e.g.
 * {@code Playing Solo SkyWars on map Dust with 8 other players}, and empty when not known yet.
 */
public record Friend(String name, boolean online, String status) {
}
