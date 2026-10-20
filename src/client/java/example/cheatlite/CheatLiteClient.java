package example.cheatlite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Główny punkt wejścia: ładuje ustawienia, rysuje HUD i otwiera menu ustawień.
 *  Prawy Shift (domyślnie) - menu ustawień. Klawisz zmienisz w Opcje -> Sterowanie.
 */
public class CheatLiteClient implements ClientModInitializer {

    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.cheatlite.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.cheatlite"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new SettingsScreen(null));
                }
            }
        });

        HudRenderCallback.EVENT.register(CheatLiteClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!Config.hudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) Config.hudX, (float) Config.hudY);
        matrices.scale(Config.hudScale, Config.hudScale);

        int y = 0;
        ctx.drawTextWithShadow(tr, "CheatLite", 0, y, 0xFFFFFFFF);
        y += 10;
        y = line(ctx, tr, "WaterCleaner", Config.waterCleaner, "", y);
        line(ctx, tr, "LeverSwap", Config.leverSwap,
                String.format(Locale.ROOT, " (zasieg %.1f)", Config.leverSwapRange), y);

        matrices.popMatrix();
    }

    private static int line(DrawContext ctx, TextRenderer tr, String name, boolean on, String extra, int y) {
        String text = name + ": " + (on ? "ON" : "OFF") + (on ? extra : "");
        ctx.drawTextWithShadow(tr, text, 0, y, on ? 0xFF55FF55 : 0xFFFF5555);
        return y + 10;
    }
}
