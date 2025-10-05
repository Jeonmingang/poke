
package com.minkang.ultimate.backpack.util;

import com.minkang.ultimate.backpack.BackpackPlugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sanitize items to avoid Netty UTFDataFormatException by trimming overly long
 * String-based NBT (e.g., giant PDC strings, extreme lore lines, book pages).
 * This keeps gameplay effectually identical while ensuring packets remain safe.
 */
public final class ItemSanitizer {
    private ItemSanitizer(){}

    public static ItemStack sanitize(ItemStack in, FileConfiguration cfg){
        if (in == null || in.getType() == Material.AIR) return in;
        try {
            if (!cfg.getBoolean("sanitize.enabled", true)) return in;
            int maxStr = Math.max(1024, cfg.getInt("sanitize.max-pdc-string-bytes", 32760));
            boolean debug = cfg.getBoolean("sanitize.debug", false);
            int maxLore = Math.max(64,   cfg.getInt("sanitize.max-lore-line-chars", 512));
            int maxPage = Math.max(64,   cfg.getInt("sanitize.max-book-page-chars", 8192));
            ItemStack it = in.clone();
            ItemMeta meta = it.getItemMeta();
            if (meta != null){
                // 1) PersistentDataContainer long strings
                try {
            if (!cfg.getBoolean("sanitize.enabled", true)) return in;
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    Set<NamespacedKey> keys = pdc.getKeys();
                    List<NamespacedKey> toRemove = new ArrayList<>();
                    for (NamespacedKey k : keys){
                        String v = pdc.get(k, PersistentDataType.STRING);
                        if (v != null){
                            // rough byte length (UTF-8) approximation
                            int bytes = v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                            if (bytes > maxStr){ toRemove.add(k); }
                        }
                    }
                    for (NamespacedKey k: toRemove){ pdc.remove(k); if (debug) { try{ BackpackPlugin.getInstance().getLogger().info("Sanitized PDC key="+k.toString()+" (oversized string)"); }catch(Throwable ignored){} } }
                } catch (Throwable ignored){}

                // 2) Lore lines too long
                try {
            if (!cfg.getBoolean("sanitize.enabled", true)) return in;
                    if (meta.hasLore()){
                        List<String> lore = meta.getLore();
                        if (lore != null){
                            List<String> trimmed = new ArrayList<>(lore.size());
                            for (String line : lore){
                                if (line == null) continue;
                                if (line.length() > maxLore) trimmed.add(line.substring(0, maxLore));
                                else trimmed.add(line);
                            }
                            meta.setLore(trimmed);
                        }
                    }
                } catch (Throwable ignored){}

                // 3) Written book giant pages
                try {
            if (!cfg.getBoolean("sanitize.enabled", true)) return in;
                    if (meta instanceof BookMeta){
                        BookMeta bm = (BookMeta) meta;
                        List<String> pages = bm.getPages();
                        if (pages != null){
                            List<String> trimmed = new ArrayList<>(pages.size());
                            for (String pg : pages){
                                if (pg == null) continue;
                                if (pg.length() > maxPage) trimmed.add(pg.substring(0, maxPage));
                                else trimmed.add(pg);
                            }
                            bm.setPages(trimmed);
                            meta = bm;
                        }
                    }
                } catch (Throwable ignored){}

                it.setItemMeta(meta);
            }
            return it;
        } catch (Throwable t){
            return in;
        }
    }

    public static ItemStack[] sanitize(ItemStack[] arr, FileConfiguration cfg){
        if (arr == null) return null;
        ItemStack[] out = new ItemStack[arr.length];
        for (int i=0;i<arr.length;i++) out[i] = sanitize(arr[i], cfg);
        return out;
    }
}
