package autotorch.autotorch.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.TorchBlock;

import java.util.Locale;

/**
 * Classifies weapons and torches using obfuscation-safe registry paths and
 * static Item TagKeys, and locates usable torch sources in the player's inventory.
 */
public class TorchInventoryClassifier {
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_INVENTORY_START = 9;
    public static final int MAIN_INVENTORY_END = 36;

    private static final TagKey<Item> TAG_SWORDS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "swords"));
    private static final TagKey<Item> TAG_AXES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "axes"));
    private static final TagKey<Item> TAG_BOWS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "bows"));
    private static final TagKey<Item> TAG_TORCHES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "torches"));

    public record TorchSource(InteractionHand hand, int hotbarSlot, int inventorySlot) {}

    public static boolean isWeaponItemPathOrTagged(String lowercasePath, boolean hasWeaponTag) {
        if (hasWeaponTag) {
            return true;
        }
        if (lowercasePath == null || lowercasePath.isEmpty()) {
            return false;
        }
        return lowercasePath.contains("sword")
                || (lowercasePath.contains("axe") && !lowercasePath.contains("pickaxe"))
                || (lowercasePath.contains("bow") && !lowercasePath.contains("bowl"))
                || lowercasePath.contains("trident");
    }

    public boolean isHoldingWeapon(LocalPlayer player) {
        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.isEmpty()) {
            return false;
        }
        Item mainItem = mainHandStack.getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(mainItem).getPath().toLowerCase(Locale.ROOT);
        boolean tagged = mainHandStack.typeHolder().is(TAG_SWORDS)
                || mainHandStack.typeHolder().is(TAG_AXES)
                || mainHandStack.typeHolder().is(TAG_BOWS);
        return isWeaponItemPathOrTagged(itemName, tagged);
    }

    public boolean isTorch(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.TORCH || item == Items.SOUL_TORCH) {
            return true;
        }
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof TorchBlock) {
            return true;
        }
        return stack.typeHolder().is(TAG_TORCHES);
    }

    public TorchSource findTorchSource(LocalPlayer player, ModConfig cdata) {
        if (isTorch(player.getOffhandItem())) {
            return new TorchSource(InteractionHand.OFF_HAND, -1, -1);
        }
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (isTorch(player.getInventory().getItem(i))) {
                return new TorchSource(InteractionHand.MAIN_HAND, i, -1);
            }
        }
        if (cdata.useInventoryTorches) {
            int targetHotbar = Mth.clamp(cdata.refillHotbarSlot - 1, 0, HOTBAR_SIZE - 1);
            for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END; i++) {
                if (isTorch(player.getInventory().getItem(i))) {
                    return new TorchSource(InteractionHand.MAIN_HAND, targetHotbar, i);
                }
            }
        }
        return null;
    }
}
