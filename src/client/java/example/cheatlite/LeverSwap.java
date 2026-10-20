package example.cheatlite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * LeverSwap: stawiasz blok w miejscu, gdzie jest dźwignia -> dźwignia znika i stoi tam twój blok.
 *
 * Działa, gdy klikniesz blokiem:
 *  - w samą dźwignię, ALBO
 *  - w dowolny sąsiedni blok tak, że miejsce postawienia bloku wypada na dźwigni
 *    (blok za nią, nad nią, pod nią, obok niej).
 *
 * Mod sam wylewa wodę przy dźwigni (woda ją zmywa), stawia twój blok i zbiera wodę do wiaderka.
 * Zasięg i zbieranie wody ustawiasz w menu (Prawy Shift). Klawisz: B (domyślnie).
 */
public class LeverSwap implements ClientModInitializer {

    private enum State { IDLE, WAIT_LEVER_GONE }

    private static final int TIMEOUT_TICKS = 60;

    private static KeyBinding toggleKey;

    private static State state = State.IDLE;
    private static BlockPos leverPos;
    private static BlockPos waterPos;
    private static BlockHitResult placeHit;
    private static Hand placeHand;
    private static int timer;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.cheatlite.leverswap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.cheatlite"));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!Config.leverSwap || !world.isClient() || state != State.IDLE) return ActionResult.PASS;
            if (!(player.getStackInHand(hand).getItem() instanceof BlockItem)) return ActionResult.PASS;

            BlockPos clickedPos = hit.getBlockPos();
            BlockState clicked = world.getBlockState(clickedPos);
            boolean onLever = clicked.isOf(Blocks.LEVER);

            // miejsce, w którym wylądowałby twój blok
            BlockPos target = onLever ? clickedPos : clickedPos.offset(hit.getSide());
            BlockState targetState = world.getBlockState(target);
            if (!targetState.isOf(Blocks.LEVER)) return ActionResult.PASS;

            Vec3d center = Vec3d.ofCenter(target);
            if (player.getEyePos().distanceTo(center) > Config.leverSwapRange) return ActionResult.PASS;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.interactionManager == null || client.player == null) return ActionResult.PASS;

            // 1. wylej wodę przy dźwigni
            BlockPos water = pourWater(client, target, center);
            if (water == null) return ActionResult.PASS;

            // 2. ustal, jak postawić blok po zniknięciu dźwigni
            BlockHitResult place;
            if (onLever) {
                Direction out = switch (targetState.get(WallMountedBlock.FACE)) {
                    case FLOOR -> Direction.UP;
                    case CEILING -> Direction.DOWN;
                    case WALL -> targetState.get(HorizontalFacingBlock.FACING);
                };
                BlockPos support = target.offset(out.getOpposite());
                Vec3d hitPos = Vec3d.ofCenter(support)
                        .add(out.getOffsetX() * 0.5, out.getOffsetY() * 0.5, out.getOffsetZ() * 0.5);
                place = new BlockHitResult(hitPos, out, support, false);
            } else {
                place = hit; // ten sam klik, który wykonałeś
            }

            leverPos = target;
            waterPos = water;
            placeHit = place;
            placeHand = hand;
            timer = 0;
            state = State.WAIT_LEVER_GONE;

            return ActionResult.FAIL; // nie przełączaj dźwigni
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            state = State.IDLE;
            return;
        }

        while (toggleKey.wasPressed()) {
            Config.leverSwap = !Config.leverSwap;
            Config.save();
            state = State.IDLE;
            client.player.sendMessage(Text.literal("LeverSwap: " + (Config.leverSwap ? "ON" : "OFF")), true);
        }

        if (state == State.WAIT_LEVER_GONE) {
            if (++timer > TIMEOUT_TICKS) {
                state = State.IDLE;
                return;
            }
            // w tym samym ticku, w którym zniknie dźwignia: stawiamy blok i zbieramy wodę
            if (!client.world.getBlockState(leverPos).isOf(Blocks.LEVER)) {
                client.interactionManager.interactBlock(client.player, placeHand, placeHit);
                client.player.swingHand(placeHand);
                state = State.IDLE;
                if (Config.leverSwapPickupWater) pickUpWater(client);
            }
        }
    }

    /** Wylewa wodę, celując w środek dźwigni. Zwraca miejsce, gdzie wylądowało źródło wody (albo null). */
    private static BlockPos pourWater(MinecraftClient client, BlockPos lever, Vec3d center) {
        ClientPlayerEntity player = client.player;

        Hand hand;
        int slot = -1;
        if (player.getOffHandStack().isOf(Items.WATER_BUCKET)) {
            hand = Hand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) { slot = i; break; }
            }
            if (slot == -1) {
                player.sendMessage(Text.literal("LeverSwap: brak wiadra z woda!"), true);
                return null;
            }
            hand = Hand.MAIN_HAND;
        }

        float oldYaw = player.getYaw();
        float oldPitch = player.getPitch();
        int oldSlot = player.getInventory().getSelectedSlot();

        aimAt(player, center);
        HitResult hr = player.raycast(player.getBlockInteractionRange(), 1.0f, false);
        if (!(hr instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(lever)) {
            player.setYaw(oldYaw);
            player.setPitch(oldPitch);
            player.sendMessage(Text.literal("LeverSwap: nie moge trafic w dzwignie (cos ja zaslania)"), true);
            return null;
        }
        BlockPos water = lever.offset(bhr.getSide());

        if (slot != -1) player.getInventory().setSelectedSlot(slot);
        client.interactionManager.interactItem(player, hand);
        if (slot != -1) player.getInventory().setSelectedSlot(oldSlot);

        player.setYaw(oldYaw);
        player.setPitch(oldPitch);
        return water;
    }

    /** Zbiera źródło wody pustym wiaderkiem (celując w nie i przywracając kamerę). */
    private static void pickUpWater(MinecraftClient client) {
        ClientPlayerEntity player = client.player;

        if (!client.world.getFluidState(waterPos).isStill()) return; // źródła już nie ma

        Hand hand;
        int slot = -1;
        if (player.getOffHandStack().isOf(Items.BUCKET)) {
            hand = Hand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).isOf(Items.BUCKET)) { slot = i; break; }
            }
            if (slot == -1) return; // brak pustego wiaderka (np. tryb kreatywny)
            hand = Hand.MAIN_HAND;
        }

        float oldYaw = player.getYaw();
        float oldPitch = player.getPitch();
        int oldSlot = player.getInventory().getSelectedSlot();

        aimAt(player, Vec3d.ofCenter(waterPos));
        if (slot != -1) player.getInventory().setSelectedSlot(slot);
        client.interactionManager.interactItem(player, hand);
        if (slot != -1) player.getInventory().setSelectedSlot(oldSlot);

        player.setYaw(oldYaw);
        player.setPitch(oldPitch);
    }

    private static void aimAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        player.setYaw((float) (MathHelper.atan2(dz, dx) * 57.29577951308232) - 90.0f);
        player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * 57.29577951308232)));
    }
}
