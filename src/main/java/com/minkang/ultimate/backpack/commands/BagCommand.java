package com.minkang.ultimate.backpack.commands;

import com.minkang.ultimate.backpack.BackpackPlugin;
import com.minkang.ultimate.backpack.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class BagCommand implements CommandExecutor {
    private final BackpackPlugin plugin;
    public BagCommand(BackpackPlugin plugin) { this.plugin = plugin; }
    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private void help(CommandSender s) {
        s.sendMessage(c("&e/가방 열기 &7- (허용 시) 개인가방 열기"));
        s.sendMessage(c("&e/가방 정보 [닉] &7- 가방 크기 확인"));
        s.sendMessage(c("&e/가방 지급아이템 <닉> [수량] &7- 가방 아이템 지급(&cop)"));
        s.sendMessage(c("&e/가방 지급확장권 <닉> [수량] &7- 확장권 지급(&cop)"));
        s.sendMessage(c("&e/가방 크기 <닉> <9|18|27|36|45|54> &7- 크기 설정(&cop)"));
        s.sendMessage(c("&e/가방 설정 [닉] &7- 손에 든 아이템을 가방으로 지정(&cop)"));
        s.sendMessage(c("&e/가방 리로드 &7- 설정 리로드(&cop)"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(sender); return true; }

        String sub = args[0];
        if (sub.equalsIgnoreCase("리로드")) {
            if (sender.isOp() || sender.hasPermission("ultimatebackpack.admin")) {
                plugin.reloadConfig();
                sender.sendMessage(c("&a설정을 리로드했습니다."));
            } else sender.sendMessage(c("&c권한이 없습니다."));
            return true;
        }

        if (sub.equalsIgnoreCase("열기")) {
            if (!(sender instanceof Player)) { sender.sendMessage("플레이어만 사용 가능합니다."); return true; }
            if (!plugin.getConfig().getBoolean("backpack.allow-command-open", true)) {
                sender.sendMessage(c("&c명령으로 열 수 없습니다. 가방 아이템을 사용하세요.")); return true;
            }
            plugin.getStorage().open((Player)sender);
            return true;
        }

        if (sub.equalsIgnoreCase("정보")) {
            Player t;
            if (args.length >= 2) {
                if (!(sender.isOp() || sender.hasPermission("ultimatebackpack.admin"))) {
                    sender.sendMessage(c("&c권한이 없습니다.")); return true;
                }
                t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(c("&c해당 플레이어를 찾을 수 없습니다.")); return true; }
            } else {
                if (!(sender instanceof Player)) { sender.sendMessage("플레이어만 사용 가능합니다."); return true; }
                t = (Player) sender;
            }
            int size = plugin.getStorage().getCurrentSize(t.getUniqueId());
            Integer next = plugin.getStorage().nextSize(size);
            sender.sendMessage(c("&a" + t.getName() + " &7가방 크기: &e" + size + " &7| 다음 단계: &e" + (next==null?"없음(최대)":next)));
            return true;
        }

        if (sub.equalsIgnoreCase("지급아이템")) {
            if (!(sender.isOp() || sender.hasPermission("ultimatebackpack.admin"))) { sender.sendMessage(c("&c권한이 없습니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(c("&c사용법: /가방 지급아이템 <닉> [수량]")); return true; }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) { sender.sendMessage(c("&c해당 플레이어를 찾을 수 없습니다.")); return true; }
            int amount = 1;
            if (args.length >= 3) { try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {} if (amount < 1) amount = 1; }
            FileConfiguration cfg = plugin.getConfig();
            String matStr = cfg.getString("starter-item.material", "CHEST");
            Material mat; try { mat = Material.valueOf(matStr); } catch (IllegalArgumentException ex) { mat = Material.CHEST; }
            String name = cfg.getString("starter-item.display-name", "&6가방");
            List<String> lore = cfg.getStringList("starter-item.lore");
            for (int i=0;i<amount;i++) {
                ItemStack it = ItemUtil.buildTaggedItem(mat, name, lore, plugin.getKeyBag(), "1");
                t.getInventory().addItem(it);
            }
            sender.sendMessage(c("&a가방 아이템 지급: &7" + t.getName() + " x" + amount));
            return true;
        }

        if (sub.equalsIgnoreCase("지급확장권")) {
            if (!(sender.isOp() || sender.hasPermission("ultimatebackpack.admin"))) { sender.sendMessage(c("&c권한이 없습니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(c("&c사용법: /가방 지급확장권 <닉> [수량]")); return true; }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) { sender.sendMessage(c("&c해당 플레이어를 찾을 수 없습니다.")); return true; }
            int amount = 1;
            if (args.length >= 3) { try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {} if (amount < 1) amount = 1; }
            FileConfiguration cfg = plugin.getConfig();
            String matStr = cfg.getString("ticket.material", "PAPER");
            Material mat; try { mat = Material.valueOf(matStr); } catch (IllegalArgumentException ex) { mat = Material.PAPER; }
            String name = cfg.getString("ticket.display-name", "&d가방 확장권");
            List<String> lore = cfg.getStringList("ticket.lore");
            ItemStack it = ItemUtil.buildTaggedItem(mat, name, lore, plugin.getKeyTicket(), "1");
            it.setAmount(amount);
            t.getInventory().addItem(it);
            sender.sendMessage(c("&a확장권 지급: &7" + t.getName() + " x" + amount));
            return true;
        }

        if (sub.equalsIgnoreCase("크기")) {
            if (!(sender.isOp() || sender.hasPermission("ultimatebackpack.admin"))) { sender.sendMessage(c("&c권한이 없습니다.")); return true; }
            if (args.length < 3) { sender.sendMessage(c("&c사용법: /가방 크기 <닉> <9|18|27|36|45|54>")); return true; }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) { sender.sendMessage(c("&c해당 플레이어를 찾을 수 없습니다.")); return true; }
            int s; try { s = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(c("&c숫자를 입력하세요.")); return true; }
            java.util.List<Integer> allowed = plugin.getConfig().getIntegerList("backpack.sizes-allowed");
            if (allowed == null || allowed.isEmpty()) allowed = java.util.Arrays.asList(9,18,27,36,45,54);
            if (!allowed.contains(s)) { sender.sendMessage(c("&c허용된 크기만 가능합니다: " + allowed.toString())); return true; }
            plugin.getStorage().setCurrentSize(t.getUniqueId(), s);
            sender.sendMessage(c("&a" + t.getName() + " 가방 크기 설정: " + s + "칸"));
            return true;
        }

        if (sub.equalsIgnoreCase("설정")) {
            if (!(sender instanceof Player)) { sender.sendMessage("플레이어만 사용 가능합니다."); return true; }
            if (!(sender.isOp() || sender.hasPermission("ultimatebackpack.admin"))) { sender.sendMessage(c("&c권한이 없습니다.")); return true; }
            Player p = (Player) sender;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) { sender.sendMessage(c("&c손에 든 아이템이 없습니다.")); return true; }

            ItemMeta meta = hand.getItemMeta();
            if (meta == null) { sender.sendMessage(c("&c이 아이템은 메타데이터를 가질 수 없습니다.")); return true; }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(plugin.getKeyBag(), PersistentDataType.STRING, "1");

            String nick = (args.length >= 2) ? args[1] : p.getName();
            String baseName = meta.hasDisplayName() ? meta.getDisplayName() : ItemUtil.prettifyMaterial(hand.getType());
            String finalName = c("&6" + baseName + " &7( &f" + nick + " &7)&f 의 가방");
            meta.setDisplayName(finalName);
            hand.setItemMeta(meta);

            sender.sendMessage(c("&a손에 든 아이템을 가방으로 지정했습니다: &f" + ChatColor.stripColor(finalName)));
            return true;
        }

        help(sender);
        return true;
    }
}
