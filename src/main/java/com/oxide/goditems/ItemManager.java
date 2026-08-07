package com.oxide.goditems;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final GodItems plugin;

    public ItemManager(GodItems plugin) {
        this.plugin = plugin;
    }

    public ItemStack createTrident() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GodItems.color(plugin.getConfig()
                    .getString("trident.name", "&b&lGOD TRIDENT")));
            meta.setLore(colorList(plugin.getConfig().getStringList("trident.lore")));
            meta.setUnbreakable(true);
            addEnchant(meta, "loyalty", 3);
            addEnchant(meta, "impaling", 5);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer()
                .set(plugin.tridentKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createCrossbow() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GodItems.color(plugin.getConfig()
                    .getString("crossbow.name", "&e&lTHUNDER CROSSBOW")));
            meta.setLore(colorList(plugin.getConfig().getStringList("crossbow.lore")));
            meta.setUnbreakable(true);
            addEnchant(meta, "quick_charge", 3);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer()
                .set(plugin.crossbowKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isGodTrident(ItemStack item) {
        return hasKey(item, Material.TRIDENT, plugin.tridentKey());
    }

    public boolean isGodCrossbow(ItemStack item) {
        return hasKey(item, Material.CROSSBOW, plugin.crossbowKey());
    }

    private boolean hasKey(ItemStack item, Material type, NamespacedKey key) {
        if (item == null || item.getType() != type) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(key, PersistentDataType.BYTE);
    }

    @SuppressWarnings("deprecation")
    private void addEnchant(ItemMeta meta, String key, int level) {
        Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (ench != null) meta.addEnchant(ench, level, true);
    }

    private List<String> colorList(List<String> input) {
        List<String> out = new ArrayList<>();
        for (String line : input) out.add(GodItems.color(line));
        return out;
    }
}
