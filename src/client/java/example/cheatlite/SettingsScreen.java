package example.cheatlite;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Menu ustawień CheatLite (przyciski i suwaki). */
public class SettingsScreen extends Screen {

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.literal("CheatLite - ustawienia"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y0 = 40;
        int step = 24;

        // --- lewa kolumna: HUD ---
        addDrawableChild(toggle(left, y0, w, "HUD", () -> Config.hudEnabled, v -> Config.hudEnabled = v));
        addDrawableChild(new Slider(left, y0 + step, w, 20, "Rozmiar HUD", 0.5f, 3.0f, 0.1f, Config.hudScale,
                v -> Config.hudScale = v));
        addDrawableChild(new Slider(left, y0 + step * 2, w, 20, "HUD pozycja X", 0, 400, 1, Config.hudX,
                v -> Config.hudX = (int) v));
        addDrawableChild(new Slider(left, y0 + step * 3, w, 20, "HUD pozycja Y", 0, 300, 1, Config.hudY,
                v -> Config.hudY = (int) v));

        addDrawableChild(toggle(left, y0 + step * 4, w, "Czyść tylko kucajac", () -> Config.waterCleanerOnlySneak,
                v -> Config.waterCleanerOnlySneak = v));

        // --- prawa kolumna: moduły ---
        addDrawableChild(toggle(right, y0, w, "WaterCleaner", () -> Config.waterCleaner, v -> Config.waterCleaner = v));
        addDrawableChild(toggle(right, y0 + step, w, "Niszcz dzwignie", () -> Config.waterCleanerRemoveLever,
                v -> Config.waterCleanerRemoveLever = v));
        addDrawableChild(new Slider(right, y0 + step * 2, w, 20, "Opoznienie (ticki)", 0, 10, 1,
                Config.waterCleanerDelay, v -> Config.waterCleanerDelay = (int) v));
        addDrawableChild(toggle(right, y0 + step * 3, w, "LeverSwap", () -> Config.leverSwap, v -> Config.leverSwap = v));
        addDrawableChild(toggle(right, y0 + step * 4, w, "Zbieraj wode", () -> Config.leverSwapPickupWater,
                v -> Config.leverSwapPickupWater = v));
        addDrawableChild(new Slider(right, y0 + step * 5, w, 20, "Zasieg (bloki)", 1.0f, 6.0f, 0.5f,
                Config.leverSwapRange, v -> Config.leverSwapRange = v));

        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private static ButtonWidget toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(label(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(name, get.getAsBoolean()));
        }).dimensions(x, y, w, 20).build();
    }

    private static Text label(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Config.save();
        if (this.client != null) this.client.setScreen(parent);
    }

    /** Prosty suwak z zakresem i krokiem. */
    private static class Slider extends SliderWidget {
        private final String label;
        private final float min, max, step;
        private final Consumer<Float> setter;

        Slider(int x, int y, int w, int h, String label, float min, float max, float step, float current,
               Consumer<Float> setter) {
            super(x, y, w, h, Text.empty(), (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            updateMessage();
        }

        private float val() {
            float v = min + (max - min) * (float) this.value;
            v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            float v = val();
            String s = step >= 1 ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
            setMessage(Text.literal(label + ": " + s));
        }

        @Override
        protected void applyValue() {
            setter.accept(val());
        }
    }
}
