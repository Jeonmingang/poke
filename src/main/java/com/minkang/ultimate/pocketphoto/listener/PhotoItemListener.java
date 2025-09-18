package com.minkang.ultimate.pocketphoto.listener;

import com.minkang.ultimate.pocketphoto.Main;
import com.minkang.ultimate.pocketphoto.util.JsonUtil;
import com.minkang.ultimate.pocketphoto.util.PixelmonSpecUtil;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class PhotoItemListener implements Listener {
    private final Main plugin;
    private final NamespacedKey dataKey;
    public PhotoItemListener(Main plugin){ this.plugin = plugin; this.dataKey = new NamespacedKey(plugin,"pp_data"); }

    @EventHandler
    public void onUse(PlayerInteractEvent e){
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(dataKey, PersistentDataType.STRING)) return;

        e.setCancelled(true);
        if (!plugin.isPixelmonPresent()) { e.getPlayer().sendMessage(ChatColor.RED + "Pixelmon 모드가 필요합니다."); return; }

        String b64 = pdc.get(dataKey, PersistentDataType.STRING);
        if (b64 == null) { e.getPlayer().sendMessage(ChatColor.RED + "사진 데이터가 손상되었습니다."); return; }
        Map<String,Object> data = JsonUtil.decode(b64);
        int slot = ((Number)data.getOrDefault("slot",1)).intValue();

        String nbt = (String) data.get("nbt");
        boolean ok = false;
        if (nbt != null && !nbt.isEmpty()) ok = PixelmonSpecUtil.restoreFromNBT(e.getPlayer(), nbt, slot);
        if (!ok){
            String safeSpec = PixelmonSpecUtil.buildSafeSpecFromMap(data); // moves 제외 스펙
            String key = plugin.getConfig().getString("pokegive-slot-key", "s");
            String cmd = "pokegive " + e.getPlayer().getName() + " " + safeSpec + " " + key + ":" + slot;
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
        }

        int amt = item.getAmount();
        if (amt <= 1) e.getPlayer().getInventory().setItemInMainHand(null);
        else item.setAmount(amt - 1);

        e.getPlayer().sendMessage(ChatColor.GREEN + "사진에서 포켓몬을 복원했습니다. (슬롯 " + slot + ")");
    }
}
