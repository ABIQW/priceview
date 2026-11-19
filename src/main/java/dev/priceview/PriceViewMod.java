package dev.priceview;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class PriceViewMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("priceview");

    @Override
    public void onInitializeClient() {
        PriceData.start();

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            String key = ItemIdResolver.resolve(stack);
            if (key == null) return; // not a SkyBlock item

            if (!PriceData.isReady()) {
                lines.add(Component.literal("Prices loading...").withStyle(ChatFormatting.GRAY));
                return;
            }

            double[] bz = PriceData.bazaar(key);
            Double bin = PriceData.bin(key);
            int count = stack.getCount();

            // Not on the market at all -> not sellable, no price shown.
            if (bz == null && bin == null) {
                lines.add(Component.literal("Sellable: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("No").withStyle(ChatFormatting.RED)));
                return;
            }

            lines.add(Component.literal("Sellable: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Yes").withStyle(ChatFormatting.GREEN)));

            if (bz != null) {
                lines.add(line("Bazaar Buy", bz[0], count));
                lines.add(line("Bazaar Sell", bz[1], count));
            }
            if (bin != null) {
                lines.add(line("Lowest BIN", bin, 1));
            }
        });
    }

    private static Component line(String label, double price, int count) {
        String text = coins(price);
        if (count > 1) text += " (x" + count + ": " + coins(price * count) + ")";
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text + " coins").withStyle(ChatFormatting.GOLD));
    }

    private static String coins(double v) {
        if (v >= 1_000_000_000) return String.format(Locale.US, "%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format(Locale.US, "%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.US, "%.1fK", v / 1_000);
        return String.format(Locale.US, "%.1f", v);
    }
}
