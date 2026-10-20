package example.cheatlite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * WaterCleaner: po wylaniu wody stawia na niej dźwignię (zastępuje wodę).
 * Dźwignia jest brana z drugiej ręki, a jeśli tam jej nie ma - z hotbara.
 * Domyślnie dźwignia zostaje postawiona (opcja "Niszcz dzwignie" ją usuwa).
 *
 *  V (domyślnie) - włącz/wyłącz. Zmiana klawisza: Opcje -> Sterowanie.
 */
public class WaterCleaner implements ClientModInitializer {

    private enum State { IDLE, WAIT_PLACE, BREAKING }

    private static KeyBinding toggleKey;

    private static State state = State.IDLE;
    private static BlockHitResult pendingHit;
    private static BlockPos leverPos;
    private static int timer;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.cheatlite.watercleaner", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.cheatlite"));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (Config.waterCleaner && world.isClient() && state == State.IDLE
                    && player.getStackInHand(hand).isOf(Items.WATER_BUCKET)
                    && (!Config.waterCleanerOnlySneak || player.isSneaking())) {
                pendingHit = hit;
                leverPos = hit.getBlockPos().offset(hit.getSide());
                timer = Math.max(1, Config.waterCleanerDelay);
                state = State.WAIT_PLACE;
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            state = State.IDLE;
            return;
        }

        while (toggleKey.wasPressed()) {
            Config.waterCleaner = !Config.waterCleaner;
            Config.save();
            state = State.IDLE;
            client.player.sendMessage(Text.literal("WaterCleaner: " + (Config.waterCleaner ? "ON" : "OFF")), true);
        }

        switch (state) {
            case WAIT_PLACE -> {
                if (--timer > 0) return;

                if (client.player.getOffHandStack().isOf(Items.LEVER)) {
                    client.interactionManager.interactBlock(client.player, Hand.OFF_HAND, pendingHit);
                    client.player.swingHand(Hand.OFF_HAND);
                } else {
                    int leverSlot = findLeverInHotbar(client);
                    if (leverSlot == -1) {
                        client.player.sendMessage(Text.literal("WaterCleaner: brak dzwigni w drugiej rece ani w hotbarze!"), true);
                        state = State.IDLE;
                        return;
                    }
                    int previous = client.player.getInventory().getSelectedSlot();
                    client.player.getInventory().setSelectedSlot(leverSlot);
                    client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, pendingHit);
                    client.player.swingHand(Hand.MAIN_HAND);
                    client.player.getInventory().setSelectedSlot(previous);
                }

                if (Config.waterCleanerRemoveLever) {
                    timer = 0;
                    state = State.BREAKING;
                } else {
                    state = State.IDLE; // dźwignia zostaje, nic więcej nie robimy
                }
            }
            case BREAKING -> {
                if (!client.world.getBlockState(leverPos).isOf(Blocks.LEVER) || timer > 60) {
                    client.interactionManager.cancelBlockBreaking();
                    state = State.IDLE;
                    return;
                }
                if (timer == 0) {
                    client.interactionManager.attackBlock(leverPos, Direction.UP);
                } else {
                    client.interactionManager.updateBlockBreakingProgress(leverPos, Direction.UP);
                }
                client.player.swingHand(Hand.MAIN_HAND);
                timer++;
            }
            default -> { }
        }
    }

    private static int findLeverInHotbar(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isOf(Items.LEVER)) return i;
        }
        return -1;
    }
}
