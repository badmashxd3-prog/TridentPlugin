package com.godtrident.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Handles creation and identification of the custom "God Trident" item.
 *
 * The item is a normal TRIDENT under the hood so it keeps vanilla
 * throwing/swim behaviour, but is tagged with:
 *  - a PersistentDataContainer flag (so the plugin can reliably identify it)
 *  - a CustomModelData value (so the resource pack can swap its texture/model)
 */
public final class ItemUtil {

    /** Must match the "custom_model_data" predicate in the resource pack's trident.json override. */
    public static final int CUSTOM_MODEL_DATA = 1001;

    private final NamespacedKey godTridentKey;

    public ItemUtil(JavaPlugin plugin) {
        this.godTridentKey = new NamespacedKey(plugin, "god_trident");
    }

    public ItemStack createGodTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "God Trident");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "A weapon forged with divine fury.",
                "",
                ChatColor.YELLOW + "Shift + Right Click " + ChatColor.GRAY + "- Sky Judgment",
                ChatColor.DARK_GRAY + "Launches you skyward. On landing, unleashes",
                ChatColor.DARK_GRAY + "a shockwave that pops nearby totems.",
                "",
                ChatColor.DARK_RED + "You take no fall damage from the launch."
        ));

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        // Purely cosmetic enchant glow + a small vanilla perk; NOT Riptide,
        // since Riptide would interfere with our own right-click behaviour.
        try {
            meta.addEnchant(Enchantment.LOYALTY, 3, true);
        } catch (Exception ignored) {
            // Enchantment constant names can shift between API versions; safe to skip.
        }

        meta.getPersistentDataContainer().set(godTridentKey, PersistentDataType.BYTE, (byte) 1);

        trident.setItemMeta(meta);
        return trident;
    }

    public boolean isGodTrident(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(godTridentKey, PersistentDataType.BYTE);
    }
}
