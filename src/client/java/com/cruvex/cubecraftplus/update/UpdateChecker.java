package com.cruvex.cubecraftplus.update;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.chat.Chat;
import com.cruvex.cubecraftplus.config.ConfigManager;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Says in chat, once per launch, when GitHub has a newer release with a jar for this Minecraft version. */
public class UpdateChecker {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/cruvex/cubecraft-plus/releases/latest");
    private static final Gson GSON = new Gson();
    /** Lands after CubeCraft's welcome banner, which would otherwise push the message out of view. */
    private static final long JOIN_DELAY_MS = 5000;

    private static UpdateChecker instance;

    // All only touched on the client thread
    private @Nullable Update update;
    private boolean announced;
    private boolean joinDelayPassed;

    public static UpdateChecker getInstance() {
        if (instance == null) {
            instance = new UpdateChecker();
        }
        return instance;
    }

    public void init() {
        if (!ConfigManager.getInstance().getConfig().updateCheck.enabled) return;

        CubeEvents.CUBE_JOIN.register(this::onCubeJoin);
        check();
    }

    private void onCubeJoin() {
        joinDelayPassed = false;
        CompletableFuture.delayedExecutor(JOIN_DELAY_MS, TimeUnit.MILLISECONDS, Minecraft.getInstance()::execute)
                .execute(() -> {
                    joinDelayPassed = true;
                    announce();
                });
    }

    private void check() {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CubeCraftPlus-fabric-mod")
                .GET()
                .build();

        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Update check: GitHub returned {}", response.statusCode());
                        return;
                    }
                    compare(GSON.fromJson(response.body(), Release.class));
                })
                .exceptionally(ex -> {
                    LOGGER.warn("Update check failed: {}", ex.getMessage());
                    return null;
                });
    }

    private void compare(@Nullable Release release) {
        if (release == null || release.tag() == null || release.page() == null) return;

        String current = modVersion(CubeCraftPlusClient.MOD_ID);
        String latest = release.tag().replaceFirst("^[vV]", "");
        String minecraft = modVersion("minecraft");
        try {
            // Ignores the "+26.2" a release jar's version carries
            Version latestVersion = SemanticVersion.parse(latest);
            boolean newer = latestVersion.compareTo(SemanticVersion.parse(current)) > 0;
            boolean forThisMinecraft = release.assets() != null && release.assets().stream()
                    .anyMatch(asset -> asset.name() != null && asset.name().endsWith("+" + minecraft + ".jar"));
            Debug.log("Update check: latest {}, running {}, jar for Minecraft {}: {}", latest, current, minecraft, forThisMinecraft);

            if (newer && forThisMinecraft) {
                Update found = new Update(latest, current.split("\\+")[0], URI.create(release.page()));
                Minecraft.getInstance().execute(() -> {
                    update = found;
                    announce();
                });
            }
        } catch (VersionParsingException | IllegalArgumentException e) {
            LOGGER.warn("Update check could not compare {} with {}: {}", latest, current, e.getMessage());
        }
    }

    private void announce() {
        Update found = update;
        if (found == null || announced || !joinDelayPassed || !CubeCraftManager.getInstance().isOnCubeCraft()) return;
        if (!ConfigManager.getInstance().getConfig().updateCheck.enabled) return;
        announced = true;

        Component download = Component.translatable("cubecraftplus.update.download")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(found.page()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cubecraftplus.update.download.tooltip"))));
        Chat.send(Component.translatable("cubecraftplus.update.available", found.latest(), found.current(), download)
                .withStyle(ChatFormatting.GREEN));
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private record Update(String latest, String current, URI page) {}

    private record Release(@SerializedName("tag_name") String tag, @SerializedName("html_url") String page, List<Asset> assets) {}

    private record Asset(String name) {}
}
