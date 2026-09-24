package com.cruvex.cubecraftplus.friends;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.ProfileNotFoundException;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Friends' heads, and the Mojang uuids behind them, kept forever so a renamed friend keeps the right head. */
public class HeadResolver {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();
    private static final TypeToken<Map<String, UUID>> IDS = new TypeToken<>() {};
    private static final TypeToken<List<GameProfile>> PROFILES = new TypeToken<>() {};
    private static final Path SKINS = ModPaths.cache("skins");
    /** How often a new head may start loading, so scrolling a long list does not burst Mojang's session server. */
    private static final long HEAD_INTERVAL_MS = 100;
    /** How long every new head waits after a failed profile request, which is usually a rate limit. */
    private static final long FAILURE_PAUSE_MS = 3000;
    /** Profile requests per uuid before it is given up on, a deleted account failing every time. */
    private static final int MAX_ATTEMPTS = 3;

    private static HeadResolver instance;

    /** Uuids by lowercase name; written on the lookup thread, read on the client thread. */
    private final Map<String, UUID> ids = new ConcurrentHashMap<>(loadIds());
    /** The last lookup queued; each one waits for the one before it. */
    private CompletableFuture<?> lookup = CompletableFuture.completedFuture(null);
    /** Lowercase names looked up this session, and the uuids of the ones Mojang found. */
    private final Set<String> asked = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> found = new ConcurrentHashMap<>();
    /** Profiles with their skin data, for vanilla to draw; saved so a new launch draws them straight away. */
    private final Map<UUID, ResolvableProfile> heads = loadHeads();
    /** Uuids fetched this session, so each saved head is checked for a new skin once. */
    private final Set<UUID> refreshed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> fetching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();
    private volatile long nextHeadAt;
    private volatile boolean headsChanged;

    public static HeadResolver getInstance() {
        if (instance == null) {
            instance = new HeadResolver();
        }
        return instance;
    }

    public @Nullable UUID idFor(String name) {
        return ids.get(name.toLowerCase(Locale.ROOT));
    }

    /** The head to draw for a friend, or null while they have no uuid or no profile yet. */
    public @Nullable ResolvableProfile headFor(Friend friend) {
        UUID id = friend.id() != null ? friend.id() : idFor(friend.name());
        if (id == null) return null;

        long now = System.currentTimeMillis();
        if (!refreshed.contains(id) && now >= nextHeadAt && failures.getOrDefault(id, 0) < MAX_ATTEMPTS && fetching.add(id)) {
            nextHeadAt = now + HEAD_INTERVAL_MS;
            fetchHead(id);
        }
        return heads.get(id);
    }

    // Fetched here rather than by vanilla, which caches a failed lookup for 10 minutes and cannot retry
    private void fetchHead(UUID id) {
        SessionService sessions = Minecraft.getInstance().services().sessionService();
        CompletableFuture.runAsync(() -> {
            ProfileResult result = sessions.fetchProfile(id, true);
            if (result != null) {
                refreshed.add(id);
                updateHead(sessions, result.profile());
            } else {
                failures.merge(id, 1, Integer::sum);
                nextHeadAt = System.currentTimeMillis() + FAILURE_PAUSE_MS;
                Debug.log("Heads: profile {} failed, pausing new heads", id);
            }
            fetching.remove(id);
        }, Util.nonCriticalIoPool());
    }

    private void updateHead(SessionService sessions, GameProfile profile) {
        ResolvableProfile old = heads.get(profile.id());
        if (old != null && Objects.equals(skinHash(sessions, old.partialProfile()), skinHash(sessions, profile))) return;

        // Swapped in once vanilla has loaded the skin, so a changed skin does not flash the default one
        ResolvableProfile head = ResolvableProfile.createResolved(profile);
        Minecraft.getInstance().playerSkinRenderCache().lookup(head).thenRun(() -> {
            heads.put(profile.id(), head);
            headsChanged = true;
        });
    }

    private static @Nullable String skinHash(SessionService sessions, GameProfile profile) {
        MinecraftProfileTexture skin = sessions.getTextures(profile).skin();
        return skin == null ? null : skin.getHash();
    }

    /** Writes the heads to disk if any changed since the last save. */
    public void saveHeads() {
        if (!headsChanged) return;

        headsChanged = false;
        write(SKINS, heads.values().stream().map(ResolvableProfile::partialProfile).toList(), PROFILES.getType());
    }

    /** Looks names up with Mojang once a session, after any running lookup; completes with the found uuids by name. */
    public CompletableFuture<Map<String, UUID>> lookUp(Collection<String> names) {
        List<String> keys = names.stream().map(name -> name.toLowerCase(Locale.ROOT)).distinct().toList();
        String[] unasked = keys.stream().filter(key -> !asked.contains(key)).toArray(String[]::new);
        asked.addAll(Arrays.asList(unasked));
        GameProfileRepository repository = Minecraft.getInstance().services().profileRepository();

        CompletableFuture<Map<String, UUID>> result = lookup.thenApplyAsync(previous -> {
            if (unasked.length > 0) {
                find(repository, unasked);
            }
            return keys.stream().filter(found::containsKey).collect(Collectors.toMap(key -> key, found::get));
        }, Util.nonCriticalIoPool());
        lookup = result.exceptionally(error -> null);
        return result;
    }

    private void find(GameProfileRepository repository, String[] names) {
        Debug.log("Heads: looking up {} names", names.length);
        // Blocks while it pages through the names ten at a time
        repository.findProfilesByNames(names, new ProfileLookupCallback() {
            @Override
            public void onProfileLookupSucceeded(String name, UUID id) {
                String key = name.toLowerCase(Locale.ROOT);
                found.put(key, id);
                ids.put(key, id);
            }

            @Override
            public void onProfileLookupFailed(String name, Exception error) {
                // Asked again next time, unless the name has no account
                if (!(error instanceof ProfileNotFoundException)) {
                    asked.remove(name.toLowerCase(Locale.ROOT));
                }
            }
        });
        write(ModPaths.playerIds(), ids, IDS.getType());
        Debug.log("Heads: found {} of {}", Arrays.stream(names).filter(found::containsKey).count(), names.length);
    }

    private static Map<String, UUID> loadIds() {
        Map<String, UUID> loaded = read(ModPaths.playerIds(), IDS);
        return loaded == null ? Map.of() : loaded;
    }

    private static Map<UUID, ResolvableProfile> loadHeads() {
        Map<UUID, ResolvableProfile> loaded = new ConcurrentHashMap<>();
        List<GameProfile> profiles = read(SKINS, PROFILES);
        if (profiles != null) {
            profiles.forEach(profile -> loaded.put(profile.id(), ResolvableProfile.createResolved(profile)));
        }
        return loaded;
    }

    private static <T> @Nullable T read(Path path, TypeToken<T> type) {
        if (!Files.exists(path)) return null;

        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, type);
        } catch (IOException | JsonParseException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to read {}, ignoring it", ModPaths.display(path), e);
            return null;
        }
    }

    private static void write(Path path, Object value, Type type) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(value, type, writer);
            }
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to save {}", ModPaths.display(path), e);
        }
    }
}
