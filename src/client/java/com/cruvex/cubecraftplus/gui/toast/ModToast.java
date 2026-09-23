package com.cruvex.cubecraftplus.gui.toast;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A titled toast with an optional icon beside the text, which vanilla's system toast has no room for. */
public class ModToast implements Toast {

    /** Nine-sliced, so it stretches when a long message wraps. */
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/now_playing");
    private static final long DISPLAY_TIME_MS = 5000L;

    private static final int ICON_SIZE = 24;
    private static final int ICON_LEFT = 4;
    private static final int TEXT_LEFT_WITH_ICON = 30;
    private static final int TEXT_LEFT_NO_ICON = 7;
    private static final int TEXT_RIGHT = 6;
    private static final int PADDING_TOP = 7;
    private static final int PADDING_BOTTOM = 3;
    private static final int LINE_SPACING = 11;
    private static final int TITLE_COLOR = 0xFFFFFF00;
    private static final int MESSAGE_COLOR = 0xFFFFFFFF;

    private final @Nullable Icon icon;
    private final Component title;
    private final List<FormattedCharSequence> messageLines;
    private Visibility visibility = Visibility.SHOW;

    public ModToast(Font font, @Nullable Icon icon, Component title, Component message) {
        this.icon = icon;
        this.title = title;
        this.messageLines = font.split(message, DEFAULT_WIDTH - textLeft() - TEXT_RIGHT);
    }

    /** Safe to call from any thread; the toast is added on the client thread. */
    public static void show(@Nullable Icon icon, Component title, Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.toastManager().addToast(new ModToast(client.font, icon, title, message)));
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (fullyVisibleForMs >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()) {
            visibility = Visibility.HIDE;
        }
    }

    @Override
    public int height() {
        return Math.max(SLOT_HEIGHT, PADDING_TOP + (1 + messageLines.size()) * LINE_SPACING + PADDING_BOTTOM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        int height = height();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, width(), height);
        if (icon != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite,
                    ICON_LEFT, (height - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);
        }

        int textLeft = textLeft();
        graphics.text(font, title, textLeft, PADDING_TOP, TITLE_COLOR, false);
        for (int line = 0; line < messageLines.size(); line++) {
            graphics.text(font, messageLines.get(line), textLeft, PADDING_TOP + (line + 1) * LINE_SPACING,
                    MESSAGE_COLOR, false);
        }
    }

    private int textLeft() {
        return icon == null ? TEXT_LEFT_NO_ICON : TEXT_LEFT_WITH_ICON;
    }

    public enum Icon {
        CUBEPANION_HAPPY("toast/cubepanion_happy"),
        CUBEPANION_SAD("toast/cubepanion_sad");

        private final Identifier sprite;

        Icon(String path) {
            this.sprite = Identifier.fromNamespaceAndPath(CubeCraftPlusClient.MOD_ID, path);
        }
    }
}
