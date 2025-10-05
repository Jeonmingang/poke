package com.minkang.ultimate.backpack.storage;

import com.minkang.ultimate.backpack.BackpackPlugin;
import com.minkang.ultimate.backpack.util.InventorySerializer;
import com.minkang.ultimate.backpack.util.ItemSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class PersonalStorage {
    private final BackpackPlugin plugin;
    private final File dirPlayers;
    private final Map<UUID, Inventory> openInv = new HashMap<>();

    public PersonalStorage(BackpackPlugin plugin) {
        this.plugin = plugin;
        this.dirPlayers = new File(plugin.getDataFolder(), "players");
        if (!dirPlayers.exists()) dirPlayers.mkdirs();
    }

    public boolean isOpen(UUID playerId) { return openInv.containsKey(playerId); }
    private File file(UUID id) { return new File(dirPlayers, id.toString() + ".yml"); }

    public int getCurrentSize(UUID id) {
        int def = plugin.getConfig().getInt("backpack.starter-size", 9);
        File f = file(id);
        if (!f.exists()) return def;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        return y.getInt("size", def);
    }

    public void setCurrentSize(UUID id, int size) {
        File f = file(id);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("size", size);
        try { y.save(f); } catch (Exception ignored) {}
    }

    public boolean isStarterGiven(UUID id) {
        File f = file(id);
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        return y.getBoolean("starterGiven", false);
    }

    public void markStarterGiven(UUID id) {
        File f = file(id);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("starterGiven", true);
        try { y.save(f); } catch (Exception ignored) {}
    }

    public ItemStack[] loadContents(UUID id) {
        File f = file(id);
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String data = y.getString("data");
        if (data == null || data.isEmpty()) return null;
        try { return InventorySerializer.itemStackArrayFromBase64(data); }
        catch (Exception e) { plugin.getLogger().warning("개인가방 로드 실패: " + e.getMessage()); return null; }
    }

    public void saveContents(UUID id, ItemStack[] contents) {
        File f = file(id);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try {
            String data = InventorySerializer.itemStackArrayToBase64(contents);
            y.set("data", data);
            y.save(f);
        } catch (Exception e) { plugin.getLogger().warning("개인가방 저장 실패: " + e.getMessage()); }
    }

    public void open(Player p) {
        UUID id = p.getUniqueId();
        int size = nearestAllowed(getCurrentSize(id));
        setCurrentSize(id, size);
        String title = plugin.getConfig().getString("backpack.title-format", "&6[개인가방] &e%player% &7(%size%)");
        title = ChatColor.translateAlternateColorCodes('&', title).replace("%player%", p.getName()).replace("%size%", String.valueOf(size));
        Inventory inv = Bukkit.createInventory(null, size, title);
        ItemStack[] contents = loadContents(id);
        if (contents != null) {
            /* no-sanitize: keep meta intact */
            // contents = ItemSanitizer.sanitize(contents, plugin.getConfig());
            inv.setContents(contents);
        }
        openInv.put(id, inv);
        p.openInventory(inv);
    }

    public void saveAndClose(Player p) {
        UUID id = p.getUniqueId();
        Inventory inv = openInv.get(id);
        if (inv != null) {
            ItemStack[] safe = inv.getContents(); // no-sanitize
            saveContents(id, safe);
        }
        openInv.remove(id);
    }

    public int nearestAllowed(int current) {
        java.util.List<Integer> allowed = plugin.getConfig().getIntegerList("backpack.sizes-allowed");
        if (allowed == null || allowed.isEmpty()) allowed = java.util.Arrays.asList(9,18,27,36,45,54);
        if (!allowed.contains(current)) {
            int starter = plugin.getConfig().getInt("backpack.starter-size", 9);
            return allowed.contains(starter) ? starter : 9;
        }
        return current;
    }

    public Integer nextSize(int current) {
        java.util.List<Integer> allowed = plugin.getConfig().getIntegerList("backpack.sizes-allowed");
        if (allowed == null || allowed.isEmpty()) allowed = java.util.Arrays.asList(9,18,27,36,45,54);
        java.util.Collections.sort(allowed);
        for (int s : allowed) if (s > current) return s;
        return null;
    }
}
