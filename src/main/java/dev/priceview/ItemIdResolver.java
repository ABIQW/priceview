package dev.priceview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/** Turns a SkyBlock item stack into the key used by the Bazaar / lowest-BIN feeds. */
public final class ItemIdResolver {
    private ItemIdResolver() {}

    /** @return market key, or null if the stack is not a SkyBlock item. */
    public static String resolve(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.copyTag();
        String id = tag.getStringOr("id", "");
        if (id.isEmpty()) return null;

        // Enchanted books are traded on the Bazaar as ENCHANTMENT_<NAME>_<LEVEL>.
        if (id.equals("ENCHANTED_BOOK")) {
            CompoundTag ench = tag.getCompoundOrEmpty("enchantments");
            if (ench.keySet().size() == 1) {
                String name = ench.keySet().iterator().next();
                int level = ench.getIntOr(name, 0);
                return "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + level;
            }
            return id;
        }

        // Pets are keyed as TYPE;TIER in the lowest-BIN feed.
        if (id.equals("PET")) {
            try {
                JsonObject info = JsonParser.parseString(tag.getStringOr("petInfo", "{}")).getAsJsonObject();
                String type = info.get("type").getAsString();
                int tier = switch (info.get("tier").getAsString()) {
                    case "COMMON" -> 0;
                    case "UNCOMMON" -> 1;
                    case "RARE" -> 2;
                    case "EPIC" -> 3;
                    case "LEGENDARY" -> 4;
                    case "MYTHIC" -> 5;
                    default -> -1;
                };
                if (tier >= 0) return type + ";" + tier;
            } catch (Exception ignored) {
                // fall through
            }
        }
        return id;
    }
}
