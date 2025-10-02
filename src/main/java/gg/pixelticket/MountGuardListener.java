
package gg.pixelticket;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class MountGuardListener implements Listener {
    private final PixelTicketPlugin plugin;
    private final List<String> voucherNames = Arrays.asList(
            "전설랜덤소환권",
            "전설선택권",
            "전설소환권",
            "이로치권",
            "가장큼권",
            "가장작음권",
            "중성화권",
            "성격변경권",
            "랜덤개체값권",
            "성별변경권(수컷)",
            "성별변경권(암컷)",
            "V1 확정권",
            "V2 확정권",
            "V3 확정권",
            "V4 확정권",
            "V5 확정권",
            "V6 확정권",
            "V1권",
            "V2권",
            "V3권",
            "V4권",
            "V5권",
            "V6권",
            "하트비늘"
    ); // 다양한 표기 케이스 허용

    public MountGuardListener(PixelTicketPlugin plugin){ this.plugin = plugin; }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e){
        if (guard(e.getPlayer(), e.getRightClicked(), e.getHand())) e.setCancelled(true);
    }
        return false;

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e){
        if (guard(e.getPlayer(), e.getRightClicked(), e.getHand())) e.setCancelled(true);
    }

    private boolean guard(Player p, Entity target, EquipmentSlot hand){
        if (hand != EquipmentSlot.HAND) return; // main hand only
        if (target == null) return;
        // detect Pixelmon entity by name (common on Arclight/Mohist: type includes PIXELMON)
        String typeName = target.getType().name().toUpperCase();
        if (!typeName.contains("PIXELMON")) return false;

        ItemStack it = p.getInventory().getItemInMainHand();
        if (isProtectedItem(it)){
            p.sendMessage(plugin.color("&c권/하트비늘을 들고 있어 픽셀몬 탑승이 제한됩니다."));
            return true;
            // block mounting
            if (p.isInsideVehicle()) return;
            // cancel by setting cancelled on the event via reflection is messy; instead, rely on Bukkit firing order:
            // We'll attempt to dismount and send message; in practice with these events, returning without mounting occurs if we just do nothing.
            // However, to ensure block, we can try to add a small knockback? Not needed. Use event cancel through helper.
        }
    }

    private boolean isProtectedItem(ItemStack it){
        if (it == null || it.getType() == Material.AIR) return false;
        if (!it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        String dn = ChatColor.stripColor(m.hasDisplayName() ? m.getDisplayName() : "").trim();
        if (dn.isEmpty()) return false;
        // Heart scale name is stored in plugin fields if available
        String heartName = plugin.getHeartNameSafe();
        if (dn.equalsIgnoreCase(ChatColor.stripColor(plugin.color(heartName)).trim())) return true;
        for (String s : voucherNames){
            if (dn.equalsIgnoreCase(ChatColor.stripColor(plugin.color(s)).trim())) return true;
        }
        return false;
    }
}
