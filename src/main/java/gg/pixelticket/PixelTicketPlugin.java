package gg.pixelticket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class PixelTicketPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public enum TicketType {
        LEG_RANDOM("전설랜덤소환권", ChatColor.LIGHT_PURPLE + "전설 랜덤 1종 지급", "LEG_RANDOM", false),
        LEG_SELECT("전설선택권", ChatColor.AQUA + "GUI에서 전설 선택 지급", "LEG_SELECT", false),
        SHINY("이로치권", ChatColor.YELLOW + "채팅에 슬롯(1-6) 입력 → 이로치 변경", "SHINY", true),
        BIGGEST("가장큼권", ChatColor.GREEN + "채팅에 슬롯 입력 → 크기 Ginormous", "BIGGEST", true),
        SMALLEST("가장작음권", ChatColor.GREEN + "채팅에 슬롯 입력 → 크기 Microscopic", "SMALLEST", true),
        NEUTER("중성화권", ChatColor.RED + "채팅에 슬롯 입력 → 번식 불가(unbreedable)", "NEUTER", true),
        RANDOM_IVS("랜덤개체값권", ChatColor.GOLD + "채팅에 슬롯 입력 → IV 전부 랜덤", "RANDOM_IVS", true),
        LEG_FORCE_SPAWN("전설소환권", ChatColor.LIGHT_PURPLE + "우클릭 즉시 전설 소환", "LEG_FORCE_SPAWN", false),
        GENDER_MALE("성별변경권(수컷)", ChatColor.AQUA + "채팅에 슬롯 입력 + 수컷으로 변경", "GENDER_MALE", true),
        GENDER_FEMALE("성별변경권(암컷)", ChatColor.AQUA + "채팅에 슬롯 입력 + 암컷으로 변경", "GENDER_FEMALE", true),
        V1("v1", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 1개 IV 31 (나머지 랜덤)", "V1", true),
        V2("v2", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 2개 IV 31 (나머지 랜덤)", "V2", true),
        V3("v3", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 3개 IV 31 (나머지 랜덤)", "V3", true),
        V4("v4", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 4개 IV 31 (나머지 랜덤)", "V4", true),
        V5("v5", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 5개 IV 31 (나머지 랜덤)", "V5", true),
        V6("v6", ChatColor.GOLD + "채팅에 슬롯 입력 + 6개 IV 전부 31", "V6", true)
        ;

        public final String displayName;
        public final String lore1;
        public final String id;
        public final boolean showHint;

        TicketType(String displayName, String lore1, String id, boolean showHint) {
            this.displayName = displayName;
            this.lore1 = lore1;
            this.id = id;
            this.showHint = showHint;
        }

        public static TicketType fromKorean(String s) {
            for (TicketType t : values()) {
                if (t.displayName.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s) || t.id.equalsIgnoreCase(s)) {
                    return t;
                }
            }
            return null;
        }
    }

    private NamespacedKey KEY_TYPE;
    private NamespacedKey KEY_TAG;

    private File legendsFile;
    private FileConfiguration legendsCfg;

    private File legendNamesFile;
    private FileConfiguration legendNamesCfg;

    private final Map<UUID, PendingAction> pending = new HashMap<>();
    static class PendingAction { final TicketType type; PendingAction(TicketType t){ this.type=t; } }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("지급")).setExecutor(this);
        Objects.requireNonNull(getCommand("지급")).setTabCompleter(this);

        KEY_TYPE = new NamespacedKey(this, "ticket_type");
        KEY_TAG = new NamespacedKey(this, "ticket_tag");

        saveResource("legendaries.yml", false);
        legendsFile = new File(getDataFolder(), "legendaries.yml");
        legendsCfg = YamlConfiguration.loadConfiguration(legendsFile);

        // Load Korean names (GUI localization)
        try { saveResource("legend_names_ko.yml", false); } catch (IllegalArgumentException ex) { /* file may already exist or not embedded; fallback below */ }
        legendNamesFile = new File(getDataFolder(), "legend_names_ko.yml");
        if (!legendNamesFile.exists()) {
            try {
                legendNamesFile.getParentFile().mkdirs();
                YamlConfiguration y = new YamlConfiguration();
                y.set("names.Mew", "뮤");
                y.set("names.Mewtwo", "뮤츠");
                y.save(legendNamesFile);
            } catch (Throwable ignore) {}
        }
        legendNamesCfg = YamlConfiguration.loadConfiguration(legendNamesFile);

        getLogger().info("PixelTicket enabled (v1.0.3).");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("pixelticket.admin")) { sender.sendMessage(color("&c권한이 없습니다.")); return true; }
        if (args.length == 0) { help(sender); return true; }

        if (args[0].equalsIgnoreCase("테스트")) {
            sender.sendMessage(color("&a[SELFTEST] &7플러그인 로드/명령/이벤트 등록 OK."));
            return true;
        }

        if (args[0].equalsIgnoreCase("설정")) {
            if (!(sender instanceof Player)) { sender.sendMessage(color("&c플레이어만 사용 가능합니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(color("&e사용법: /지급 설정 <권종류>")); return true; }
            TicketType t = TicketType.fromKorean(args[1]);
            if (t == null) { sender.sendMessage(color("&c권종류를 찾을 수 없습니다.")); return true; }
            Player p = (Player) sender;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) { p.sendMessage(color("&c손에 든 아이템이 없습니다.")); return true; }
            markAsTicket(hand, t);
            p.sendMessage(color("&a설정 완료: &f") + t.displayName + color("&7 (손에 든 아이템이 해당 권으로 설정됨)"));
            return true;
        }

        if (args.length < 3) { help(sender); return true; }
        TicketType t = TicketType.fromKorean(args[0]);
        if (t == null) { sender.sendMessage(color("&c권종류를 찾을 수 없습니다.")); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(color("&c해당 플레이어를 찾을 수 없습니다: &f")+args[1]); return true; }
        int amt; try { amt=Integer.parseInt(args[2]); } catch (Exception e) { sender.sendMessage(color("&c갯수는 숫자여야 합니다.")); return true; }
        ItemStack item = createTicket(t, amt);
        target.getInventory().addItem(item);
        sender.sendMessage(color("&a지급 완료: &f")+t.displayName+color("&7 x ")+amt+color("&7 → &f")+target.getName());
        target.sendMessage(color("&d[권 지급] &f")+t.displayName+color("&7 x ")+amt+color(" &7을 받았습니다."));
        return true;
    }

    private void help(CommandSender s){
        s.sendMessage(color("&b/지급 <권종류> <플레이어> <갯수>"));
        s.sendMessage(color("&7권종류: &f전설랜덤소환권, 전설선택권, 이로치권, 가장큼권, 가장작음권, 중성화권, 랜덤개체값권, 전설소환권"));
        s.sendMessage(color("&b/지급 설정 <권종류> &7- 손에 든 아이템을 해당 권으로 태그"));
        s.sendMessage(color("&b/지급 테스트 &7- 플러그인 자체 점검"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("설정","테스트"));
            base.addAll(Arrays.stream(TicketType.values()).map(t->t.displayName).collect(Collectors.toList()));
            return base.stream().filter(s->s.startsWith(args[0])).collect(Collectors.toList());
        }
        if (args.length == 2 && "설정".equalsIgnoreCase(args[0]))
            return Arrays.stream(TicketType.values()).map(t->t.displayName).filter(s->s.startsWith(args[1])).collect(Collectors.toList());
        return Collections.emptyList();
    }

    private ItemStack createTicket(TicketType t, int amount) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(color("&6[ &e소모권 &6] &f") + t.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(color("&7픽셀몬 소모권"));
        lore.add(color("&6") + t.lore1.replace("→", "+"));
        lore.add(color("&8우클릭 사용 · 채팅 안내에 따르세요"));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        m.addEnchant(Enchantment.LUCK, 1, true);
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(KEY_TYPE, PersistentDataType.STRING, t.id);
        pdc.set(KEY_TAG, PersistentDataType.STRING, "PIXELTICKET");
        it.setItemMeta(m);
        it.setAmount(Math.max(1, amount));
        return it;
    }

    private void markAsTicket(ItemStack it, TicketType t) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(color("&6[ &e소모권 &6] &f") + t.displayName);
        List<String> lore = new ArrayList<>();
        lore.add(color("&7픽셀몬 소모권"));
        lore.add(color("&6") + t.lore1.replace("→", "+"));
        lore.add(color("&8우클릭 사용 · 채팅 안내에 따르세요"));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        m.addEnchant(Enchantment.LUCK, 1, true);
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(KEY_TYPE, PersistentDataType.STRING, t.id);
        pdc.set(KEY_TAG, PersistentDataType.STRING, "PIXELTICKET");
        it.setItemMeta(m);
    }

    private boolean isTicket(ItemStack it){
        if (it==null || it.getType()==Material.AIR || !it.hasItemMeta()) return false;
        return "PIXELTICKET".equals(it.getItemMeta().getPersistentDataContainer().get(KEY_TAG, PersistentDataType.STRING));
    }

    private TicketType getType(ItemStack it){
        if (!isTicket(it)) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        if (id==null) return null;
        for (TicketType t: TicketType.values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }

    private void consumeOne(Player p){
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand==null) return;
        int amt = hand.getAmount();
        if (amt<=1) p.getInventory().setItemInMainHand(null); else hand.setAmount(amt-1);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e){
        if (e.getHand() != EquipmentSlot.HAND) return; // prevent offhand duplicate
        if (e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isTicket(hand)) return;
        e.setCancelled(true);
        TicketType type = getType(hand);
        if (type==null) return;

        switch (type){
            case LEG_RANDOM:
                String species = pickRandomLegend();
                runConsole("pokegive "+p.getName()+" "+species);
                p.sendMessage(color("&d[권 사용] &f전설 랜덤 지급: &b")+species);
                consumeOne(p);
                break;

            case LEG_SELECT:
                // pre-consume then open GUI (prevents stash exploit)
                consumeOne(p);
                openLegendGui(p, 0);
                break;

            case SHINY:
            case BIGGEST:
            case SMALLEST:
            case NEUTER:
            case RANDOM_IVS:
            case GENDER_MALE:
            case GENDER_FEMALE:
            case V1:
            case V2:
            case V3:
            case V4:
            case V5:
            case V6:
            case LEG_FORCE_SPAWN:
            case GENDER_MALE:
            case GENDER_FEMALE:
                // pre-consume BEFORE action (prevents stash exploit)
                consumeOne(p);
                if (type == TicketType.LEG_FORCE_SPAWN) { runConsole("spawnlegendary " + p.getName()); } else { askSlotThen(p, type); }
                break;
        }
    }

    private String pickRandomLegend(){
        List<String> legends = legendsCfg.getStringList("legendaries");
        if (legends==null || legends.isEmpty()) return "Mewtwo";
        return legends.get(new java.util.Random().nextInt(legends.size()));
    }

    private void askSlotThen(Player p, TicketType type){
        p.sendMessage(color("&a채팅에 &f1~6 &a중 하나의 숫자를 입력하세요. (해당 슬롯 적용)"));
        pending.put(p.getUniqueId(), new PendingAction(type));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        UUID u = e.getPlayer().getUniqueId();
        if (!pending.containsKey(u)) return;
        e.setCancelled(true);
        String msg = e.getMessage().trim();
        int slot;
        try { slot = Integer.parseInt(msg); } catch (Exception ex) { e.getPlayer().sendMessage(color("&c숫자(1~6)를 입력하세요.")); return; }
        if (slot<1 || slot>6) { e.getPlayer().sendMessage(color("&c슬롯은 1~6 범위여야 합니다.")); return; }
        PendingAction pa = pending.remove(u);
        Player p = e.getPlayer();
        new BukkitRunnable(){ @Override public void run(){ handleSlotAction(p, pa.type, slot); } }.runTask(this);
    }

    private void handleSlotAction(Player p, TicketType type, int slot){
        switch (type){
            case SHINY:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" shiny",
                        "pokeedit "+p.getName()+" "+slot+" shiny:true",
                        "pokeedit "+p.getName()+" "+slot+" s:1"
                );
                p.sendMessage(color("&e[이로치권] &f슬롯 "+slot+" 포켓몬을 이로치로 변경 시도."));
                break;
            case BIGGEST:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gr:Ginormous",
                        "pokeedit "+p.getName()+" "+slot+" growth:Ginormous",
                        "pokeedit "+p.getName()+" "+slot+" growth:ginormous"
                );
                p.sendMessage(color("&a[가장큼권] &f슬롯 "+slot+" 크기를 Ginormous로 변경 시도."));
                break;
            case SMALLEST:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gr:Microscopic",
                        "pokeedit "+p.getName()+" "+slot+" growth:Microscopic",
                        "pokeedit "+p.getName()+" "+slot+" growth:microscopic"
                );
                p.sendMessage(color("&a[가장작음권] &f슬롯 "+slot+" 크기를 Microscopic으로 변경 시도."));
                break;
            case NEUTER:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" unbreedable",
                        "pokeedit "+p.getName()+" "+slot+" unbreedable:true",
                        "pokeedit "+p.getName()+" "+slot+" breedable:false"
                );
                p.sendMessage(color("&c[중성화권] &f슬롯 "+slot+" 번식 불가 설정 시도."));
                break;
            case RANDOM_IVS:
            case GENDER_MALE:
            case GENDER_FEMALE:
            case V1:
            case V2:
            case V3:
            case V4:
            case V5:
            case V6:
                java.util.Random r = new java.util.Random();
                int hp=r.nextInt(32), atk=r.nextInt(32), def=r.nextInt(32), spa=r.nextInt(32), spd=r.nextInt(32), spe=r.nextInt(32);
                tryCommands(
                        String.format("pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspd:%d", p.getName(), slot, hp, atk, def, spa, spd, spe),
                        String.format("pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspeed:%d", p.getName(), slot, hp, atk, def, spa, spd, spe),
                        String.format("pokeedit %s %d ivhp:%d", p.getName(), slot, hp),
                        String.format("pokeedit %s %d ivatk:%d", p.getName(), slot, atk),
                        String.format("pokeedit %s %d ivdef:%d", p.getName(), slot, def),
                        String.format("pokeedit %s %d ivspatk:%d", p.getName(), slot, spa),
                        String.format("pokeedit %s %d ivspdef:%d", p.getName(), slot, spd),
                        String.format("pokeedit %s %d ivspd:%d", p.getName(), slot, spe),
                        String.format("pokeedit %s %d ivspeed:%d", p.getName(), slot, spe)
                );
                p.sendMessage(color("&6[랜덤개체값권] &f슬롯 "+slot+" IV 랜덤화 시도."));
                break;
            case LEG_FORCE_SPAWN:
            case LEG_SELECT:
            case LEG_RANDOM:
                break;
            case GENDER_MALE:
            case GENDER_FEMALE:
                break;
        }
    }

    private void tryCommands(String... commands){
        for (String c : commands) {
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c); } catch (Throwable ignored) {}
        }
    }

    private void runConsole(String command){ Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command); }

    private void openLegendGui(Player p, int page){
        java.util.List<String> legends = legendsCfg.getStringList("legendaries");
        int perPage = 45;
        int maxPage = Math.max(1, (legends.size()+perPage-1)/perPage);
        if (page<0) page=0; if (page>=maxPage) page = maxPage-1;
        Inventory inv = Bukkit.createInventory(new LegendHolder(page), 54, color("&9전설 선택 ("+(page+1)+"/"+maxPage+")"));
        int start = page*perPage;
        for (int i=0;i<perPage && start+i<legends.size();i++){
            String name = legends.get(start+i);
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(color("&6[ &e소모권 &6] &f") + t.displayName);
            m.setLore(java.util.Arrays.asList(color("&7클릭 시 지급: &f")+localize(name), color("&8영문: &7")+name, color("&8/pokegive "+p.getName()+" "+name)));
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
        inv.setItem(45, navItem("&a이전 페이지", Material.ARROW));
        inv.setItem(53, navItem("&a다음 페이지", Material.ARROW));
        inv.setItem(49, navItem("&d닫기", Material.BARRIER));
        p.openInventory(inv);
    }

    private String localize(String species){
        try {
            if (legendNamesCfg != null) {
                String v = legendNamesCfg.getString("names." + species);
                if (v != null && !v.trim().isEmpty()) return v;
            }
        } catch (Throwable ignored) {}
        return species;
    }

    private ItemStack navItem(String name, Material mat){
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(color("&6[ &e소모권 &6] &f") + t.displayName);
        it.setItemMeta(m);
        return it;
    }

    static class LegendHolder implements InventoryHolder { final int page; LegendHolder(int page){ this.page=page; } @Override public Inventory getInventory(){ return null; } }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        if (!(e.getInventory().getHolder() instanceof LegendHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked==null || clicked.getType()==Material.AIR) return;
        LegendHolder holder = (LegendHolder) e.getInventory().getHolder();
        int slot = e.getRawSlot();
        if (slot==45){ openLegendGui(p, Math.max(0, holder.page-1)); return; }
        if (slot==53){ openLegendGui(p, holder.page+1); return; }
        if (slot==49){ p.closeInventory(); return; }
        if (slot>=0 && slot<45 && clicked.hasItemMeta()){
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            // name may be Korean; to issue correct command, lookup original English from lore last line
            List<String> lore = clicked.getItemMeta().getLore();
            String eng = null;
            if (lore != null) {
                for (String line : lore) {
                    String s = ChatColor.stripColor(line);
                    if (s.startsWith("영문: ")) {
                        eng = s.substring("영문: ".length()).trim();
                    }
                }
            }
            if (eng == null || eng.isEmpty()) eng = name; // fallback
            runConsole("pokegive "+p.getName()+" "+eng);
            p.sendMessage(color("&b[전설선택권] &f지급 완료: &b")+localize(eng));
            p.closeInventory();
        }
    }

    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s); }
}


private void applyVIV(Player p, int slot, int n){
    java.util.Random rnd = new java.util.Random();
    int hp = rnd.nextInt(32), atk = rnd.nextInt(32), def = rnd.nextInt(32),
        spa = rnd.nextInt(32), spd = rnd.nextInt(32), spe = rnd.nextInt(32);

    java.util.List<Integer> idx = new java.util.ArrayList<>();
    for (int i=0;i<6;i++) idx.add(i);
    java.util.Collections.shuffle(idx);
    for (int i=0;i<n && i<6;i++){
        switch (idx.get(i)){
            case 0: hp = 31; break;
            case 1: atk = 31; break;
            case 2: def = 31; break;
            case 3: spa = 31; break;
            case 4: spd = 31; break;
            case 5: spe = 31; break;
        }
    }

    String cmd1 = String.format(
            "pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspd:%d",
            p.getName(), slot, hp, atk, def, spa, spd, spe
    );
    String cmd2 = String.format(
            "pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspeed:%d",
            p.getName(), slot, hp, atk, def, spa, spd, spe
    );
    tryCommands(cmd1, cmd2);

    p.sendMessage(color("&6[" + n + "V권] &f슬롯 " + slot + "의 IV 적용(선택 " + n + "개=31, 나머지 랜덤)."));
}
