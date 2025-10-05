package com.minkang.ultimate.backpack.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemUtil {
    public static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public static ItemStack buildTaggedItem(Material mat, String name, List<String> lore,
                                            org.bukkit.NamespacedKey key, String value) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> cl = new ArrayList<>();
                for (String l : lore) cl.add(color(l));
                meta.setLore(cl);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (key != null && value != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(key, PersistentDataType.STRING, value);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    public static boolean hasTag(ItemStack it, org.bukkit.NamespacedKey key, String value) {
        if (it == null || it.getType() == Material.AIR) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        String v = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return value.equals(v);
    }

    public static String prettifyMaterial(Material m) {
        String s = m.name().toLowerCase().replace('_',' ');
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
