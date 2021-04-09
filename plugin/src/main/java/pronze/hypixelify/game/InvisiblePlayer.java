package pronze.hypixelify.game;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.lib.bukkit.utils.nms.ClassStorage;
import org.screamingsandals.bedwars.lib.utils.Pair;
import org.screamingsandals.bedwars.lib.utils.reflect.Reflect;
import pronze.hypixelify.SBAHypixelify;
import pronze.hypixelify.utils.SBAUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Data
public class InvisiblePlayer {
    private final Player player;
    private final Arena arena;

    private int ticksPassed;
    private int timePassed;
    private boolean isHidden;
    private CachedLocationModal lastLocation;
    protected BukkitTask footStepSoundTracker;
    protected BukkitTask armorHider;

    public void vanish() {
        if (isHidden) return;
        isHidden = true;
        hideArmor();

        lastLocation = CachedLocationModal.from(player.getLocation());
        footStepSoundTracker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isElligble() || !isHidden) {
                    arena.removeHiddenPlayer(player);
                    showPlayer();
                    this.cancel();
                    return;
                }
                if (ticksPassed % 20 == 0) {
                    timePassed++;
                }
                if (lastLocation.isSimilarTo(player.getLocation())) {
                    return;
                }
                lastLocation = CachedLocationModal.from(player.getLocation());
                Location location = player.getLocation();
                location.setY(Math.floor(location.getY()));

                if (!player.getLocation().clone().subtract(0, 1, 0).getBlock().isEmpty()) {
                    //TODO: play effect
                }
                ticksPassed += 10;
            }
        }.runTaskTimer(SBAHypixelify.getInstance(), 0L, 10L);

        armorHider = new BukkitRunnable() {
            @Override
            public void run() {
                if (isElligble() && isHidden) {
                    hideArmor();
                } else {
                    showArmor();
                    this.cancel();
                }
            }
        }.runTaskTimer(SBAHypixelify.getInstance(), 0L, 1L);
    }

    private boolean isElligble() {
        return arena.getGame().getStatus() == GameStatus.RUNNING
                && player.isOnline()
                && player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                && arena.getGame().getConnectedPlayers().contains(player);
    }


    private void showArmor() {
        final var packets = new ArrayList<>();

        final var boots = player.getInventory().getBoots();
        final var helmet = player.getInventory().getHelmet();
        final var chestplate = player.getInventory().getChestplate();
        final var leggings = player.getInventory().getLeggings();

        final var nmsBoot = stackAsNMS(boots == null ? new ItemStack(Material.AIR) : boots);
        final var nmsChestPlate = stackAsNMS(chestplate == null ? new ItemStack(Material.AIR) : boots);
        final var nmsLeggings = stackAsNMS(leggings == null ? new ItemStack(Material.AIR) : leggings);
        final var nmsHelmet = stackAsNMS(boots == null ? new ItemStack(Material.AIR) : helmet);

        final var headSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.HEAD);
        final var chestplateSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.CHEST);

        final var legsSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.LEGS);

        final var feetSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.FEET);

        packets.add(getEquipmentPacket(player, nmsHelmet, headSlot));
        packets.add(getEquipmentPacket(player, nmsChestPlate, chestplateSlot));
        packets.add(getEquipmentPacket(player, nmsLeggings, legsSlot));
        packets.add(getEquipmentPacket(player, nmsBoot, feetSlot));

        arena
                .getGame()
                .getConnectedPlayers()
                .forEach(pl -> ClassStorage.sendPacket(pl, packets));
    }

    private void hideArmor() {
        final var airStack = stackAsNMS(new ItemStack(Material.AIR));
        final var packets = new ArrayList<>();

        final var headSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.HEAD);
        final var chestplateSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.CHEST);

        final var legsSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.LEGS);

        final var feetSlot = Reflect
                .getMethod(ClassStorage.NMS.CraftEquipmentSlot, "getNMS", EquipmentSlot.class)
                .invokeStatic(EquipmentSlot.FEET);

        packets.add(getEquipmentPacket(player, airStack, headSlot));
        packets.add(getEquipmentPacket(player, airStack, chestplateSlot));
        packets.add(getEquipmentPacket(player, airStack, legsSlot));
        packets.add(getEquipmentPacket(player, airStack, feetSlot));

        arena
                .getGame()
                .getConnectedPlayers()
                .stream().filter(pl -> !pl.equals(player))
                .forEach(pl -> ClassStorage.sendPacket(pl, packets));
    }

    private Object getEquipmentPacket(Player entity, Object stack, Object chestplateSlot) {
        final var reference = new AtomicReference<>();

        Reflect.constructor(ClassStorage.NMS.PacketPlayOutEntityEquipment, int.class, List.class)
                .ifPresentOrElse(
                        constructor ->
                                reference.set(constructor.construct(entity.getEntityId(), List.of(Pair.of(chestplateSlot, stack)))),
                        () ->
                                reference.set(
                                        Reflect.constructor(ClassStorage.NMS.PacketPlayOutEntityEquipment, int.class, ClassStorage.NMS.EnumItemSlot, ClassStorage.NMS.ItemStack)
                                                .construct(chestplateSlot, chestplateSlot, stack)
                                )
                );
        return reference.get();
    }

    private void showPlayer() {
        isHidden = false;
        SBAUtil.cancelTask(footStepSoundTracker);
    }

    private Object stackAsNMS(ItemStack item) {
        return Reflect.getMethod(ClassStorage.NMS.CraftItemStack, "asNMSCopy", ItemStack.class).invokeStatic(item);
    }


    @Data
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CachedLocationModal {
        private final double x;
        private final double y;
        private final double z;

        public static CachedLocationModal from(Location location) {
            return new CachedLocationModal(location.getX(), location.getY(), location.getZ());
        }

        public boolean isSimilarTo(@NotNull Location location) {
            return location.getX() == x && location.getY() == y && location.getZ() == z;
        }
    }
}
