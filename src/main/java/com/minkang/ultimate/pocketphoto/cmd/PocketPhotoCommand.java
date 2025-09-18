package com.minkang.ultimate.pocketphoto.cmd;

import com.minkang.ultimate.pocketphoto.Main;
import com.minkang.ultimate.pocketphoto.util.PhotoItemUtil;
import com.minkang.ultimate.pocketphoto.util.PixelmonSpecUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PocketPhotoCommand implements CommandExecutor {
    private final Main plugin;
    public PocketPhotoCommand(Main plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "플레이어만 사용 가능합니다."); return true; }
        Player p = (Player) sender;
        if (!plugin.isPixelmonPresent()) { p.sendMessage(ChatColor.RED + "Pixelmon 모드가 필요합니다."); return true; }
        if (args.length != 1) { p.sendMessage(ChatColor.YELLOW + "/포켓사진 <1~6>"); return true; }
        int slot;
        try { slot = Integer.parseInt(args[0]); } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED + "숫자 슬롯만 입력하세요 (1~6)."); return true; }
        if (slot < 1 || slot > 6) { p.sendMessage(ChatColor.RED + "슬롯은 1~6 입니다."); return true; }

        PixelmonSpecUtil.Result res = PixelmonSpecUtil.extractAndRemovePartyPokemonWithNBT(p, slot);
        if (!res.success) { p.sendMessage(ChatColor.RED + res.message); return true; }

        boolean added = PhotoItemUtil.givePhotoItem(p, res);
        if (!added) { p.getWorld().dropItemNaturally(p.getLocation(), PhotoItemUtil.buildPhotoItem(p, res)); }
        p.sendMessage(ChatColor.GREEN + "포켓몬을 사진으로 변환했습니다: " + ChatColor.WHITE + res.species + ChatColor.GRAY + " (슬롯 " + slot + ")");
        return true;
    }
}
