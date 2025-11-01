// Java 8 / Spigot 1.16.5
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
    private final java.util.Map<String,String> moveAlias = new java.util.HashMap<>();
    private final java.util.Map<String,String> natureAlias = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID,Integer> natureSlotWaiting = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, org.bukkit.inventory.ItemStack> voucherRefund = new java.util.HashMap<>();


    public enum TicketType {
        LEG_RANDOM("전설랜덤권", ChatColor.LIGHT_PURPLE + "우클릭 즉시 전설 랜덤 지급", "LEG_RANDOM", false),
        LEG_SELECT("전설선택권", ChatColor.AQUA + "GUI에서 전설 선택 지급", "LEG_SELECT", false),
        SHINY("이로치권", ChatColor.YELLOW + "채팅에 슬롯 입력 + 이로치 변경", "SHINY", true),
        BIGGEST("가장큼권", ChatColor.GREEN + "채팅에 슬롯 입력 + 크기 Ginormous", "BIGGEST", true),
        SMALLEST("가장작음권", ChatColor.GREEN + "채팅에 슬롯 입력 + 크기 Microscopic", "SMALLEST", true),
        NEUTER("중성화권", ChatColor.RED + "채팅에 슬롯 입력 + 번식 불가", "NEUTER", true),
        NATURE_CHANGE("성격변경권", ChatColor.LIGHT_PURPLE + "채팅에 슬롯 입력 + 성격 랜덤 변경", "NATURE_CHANGE", true),
        NATURE_FIX("성격변경권(확정)", ChatColor.LIGHT_PURPLE + "채팅: 슬롯 선택 후 #성격 입력 → 해당 성격으로 변경", "NATURE_FIX", true),
        ABILITY_PATCH("특성패치", ChatColor.AQUA + "채팅에 슬롯 입력 + 드림특성 적용 & 교환/교배 불가", "ABILITY_PATCH", true),
        RANDOM_IVS("랜덤개체값권", ChatColor.GOLD + "채팅에 슬롯 입력 + IV 전부 랜덤", "RANDOM_IVS", true),
        IV_LOCK_RANDOM("개체값고정랜덤", ChatColor.GOLD + "채팅에 슬롯 입력 → [스탯] 선택 → 해당 스탯만 랜덤", "IV_LOCK_RANDOM", true),
        IV_LOCK_MAX("개체값고정최대", ChatColor.GOLD + "채팅에 슬롯 입력 → [스탯] 선택 → 해당 스탯만 31", "IV_LOCK_MAX", true),
        LEG_FORCE_SPAWN("전설소환권", ChatColor.LIGHT_PURPLE + "우클릭 즉시 전설 소환", "LEG_FORCE_SPAWN", false),
        GENDER_MALE("성별변경권(수컷)", ChatColor.AQUA + "채팅에 슬롯 입력 + 수컷으로 변경", "GENDER_MALE", true),
        GENDER_FEMALE("성별변경권(암컷)", ChatColor.AQUA + "채팅에 슬롯 입력 + 암컷으로 변경", "GENDER_FEMALE", true),
        V1("V1 확정권", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 1개 IV 31 (나머지 랜덤)", "V1", true),
        V2("V2 확정권", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 2개 IV 31 (나머지 랜덤)", "V2", true),
        V3("V3 확정권", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 3개 IV 31 (나머지 랜덤)", "V3", true),
        V4("V4 확정권", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 4개 IV 31 (나머지 랜덤)", "V4", true),
        V5("V5 확정권", ChatColor.GOLD + "채팅에 슬롯 입력 + 랜덤 5개 IV 31 (나머지 랜덤)", "V5", true),
        V6("V6 확정권", ChatColor.GOLD + "채팅에 슬롯 입력 + 6개 IV 전부 31", "V6", true),
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

        static TicketType fromKorean(String s) {
            if (s == null) return null;
            String raw = s.trim();
            String norm = raw.replace(" ", "").replace("(", "").replace(")", "").toLowerCase();

            for (TicketType t : values()) {
                String dn = t.displayName == null ? "" : t.displayName.replace(" ", "").replace("(", "").replace(")", "").toLowerCase();
                String idn = t.id == null ? "" : t.id.replace(" ", "").replace("(", "").replace(")", "").toLowerCase();
                if (t == ABILITY_PATCH) {
                    // ABILITY_PATCH는 정확히 '특성패치'만 허용 (공백 불가)
                    if (raw.equalsIgnoreCase("특성패치")) return ABILITY_PATCH;
                } else {
                    if (dn.equals(norm) || idn.equals(norm) || t.name().equalsIgnoreCase(raw)) return t;
                }
            }
            return null;
        }
    
    }
    private NamespacedKey KEY_TYPE;
    private NamespacedKey KEY_TAG;
    private NamespacedKey KEY_VER;
    private static final int TICKET_VERSION = 2;
    private NamespacedKey HEART_KEY;

    
    private NamespacedKey HEART_VER;
    private static final int HEART_VERSION = 2;
private File legendsFile;
    private FileConfiguration legendsCfg;

    private File legendNamesFile;
    private FileConfiguration legendNamesCfg;

    private final Map<UUID, PendingAction> pending = new HashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> lastUseMs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long USE_COOLDOWN_MS = 350;

    static class PendingAction { final TicketType type; PendingAction(TicketType t){ this.type=t; } }

    static class IvPending { final TicketType type; final int slot; IvPending(TicketType t,int s){this.type=t;this.slot=s;} }
    private final java.util.Map<java.util.UUID, IvPending> ivLockWaiting = new java.util.HashMap<>();

    @Override
    public void onEnable(){
        saveDefaultConfig();
        loadMoveAliases();
        loadNatureAliases();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("지급")).setExecutor(this);
        Objects.requireNonNull(getCommand("하트비늘")).setExecutor(this);
        Objects.requireNonNull(getCommand("지급")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("하트비늘")).setTabCompleter(this);

        KEY_TYPE = new NamespacedKey(this, "ticket_type");
        KEY_VER  = new NamespacedKey(this, "ticket_ver");
        KEY_TAG = new NamespacedKey(this, "ticket_tag");
        HEART_KEY = new NamespacedKey(this, "heart_scale");

        
        HEART_VER = new NamespacedKey(this, "heart_scale_ver");
saveResource("legendaries.yml", false);
        legendsFile = new File(getDataFolder(), "legendaries.yml");
        legendsCfg = YamlConfiguration.loadConfiguration(legendsFile);

        // GUI 한글 매핑 로드(없으면 폴백 생성)
        try { saveResource("legend_names_ko.yml", false); } catch (IllegalArgumentException ex) { }
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
        // 성격 한글 매핑 기본파일 배포
        try { saveResource("natures_ko.yml", false); } catch (IllegalArgumentException ex) { }


        getLogger().info("PixelTicket enabled (v1.0.4).");
    }

    @Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        
        if (cmd.getName().equalsIgnoreCase("하트비늘")){
            // OP-only: all heartscale commands require admin; item use remains event-driven and open to all.
            if (!(args.length>0 && args[0].equalsIgnoreCase("취소")) && !sender.hasPermission("pixelticket.admin")) { sender.sendMessage(color("&c권한이 없습니다.")); return true; }

            if (args.length > 0 && (args[0].equalsIgnoreCase("리로드") || args[0].equalsIgnoreCase("reload"))){
                if (!sender.hasPermission("pixelticket.admin")) { sender.sendMessage(color("&c권한이 없습니다.")); return true; }
                loadMoveAliases();
                loadNatureAliases();
                sender.sendMessage(color("&a한글화 파일을 다시 불러왔습니다. &fmoves_ko.yml: &e"+moveAlias.size()+" &7/ &fnatures_ko.yml: &e"+natureAlias.size()));
                return true;
            }
            if (args.length==0){ sender.sendMessage(color("&d/하트비늘 취소, /하트비늘 아이템, /하트비늘 지급 <플레이어> <개수> | /하트비늘 reload")); return true; }
            if (args[0].equalsIgnoreCase("취소")){
                if (!(sender instanceof org.bukkit.entity.Player)){ sender.sendMessage("플레이어만 사용 가능"); return true; }
                org.bukkit.entity.Player p=(org.bukkit.entity.Player)sender;
                java.util.UUID u = p.getUniqueId();
                org.bukkit.inventory.ItemStack refund = heartRefund.remove(u);
                if (refund!=null) p.getInventory().addItem(refund);
                heartSlotWaiting.remove(u);
                sender.sendMessage(color("&7작업을 취소했습니다."));
                return true;
            }
            if (args[0].equalsIgnoreCase("아이템")){
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
                return true;
            }
            org.bukkit.entity.Player p = (org.bukkit.entity.Player) sender;
            org.bukkit.inventory.ItemStack held = p.getInventory().getItemInMainHand();
            saveHeartTemplateFrom(held);
            org.bukkit.inventory.ItemStack heart = createHeartFromTemplate(Math.max(1, held.getAmount()));
            p.getInventory().setItemInMainHand(heart);
            sender.sendMessage(color("&d[하트비늘] 손에 든 아이템을 템플릿으로 저장하고, 해당 설정으로 변환했습니다."));
            return true;
    
            }
            if (args[0].equalsIgnoreCase("지급")){
                if (args.length<3){ sender.sendMessage(color("&c사용법: /하트비늘 지급 <플레이어> <개수>")); return true; }
                org.bukkit.entity.Player t = org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(color("&c플레이어를 찾을 수 없습니다.")); return true; }
                int cnt; 
                try { cnt = Integer.parseInt(args[2]); } catch (Exception ex){ sender.sendMessage(color("&c개수는 숫자.")); return true; }
                org.bukkit.inventory.ItemStack it = createHeartFromTemplate(Math.max(1, cnt));
                t.getInventory().addItem(it);
                sender.sendMessage(color("&a지급 완료: &f")+t.getName()+" &7x"+cnt);
                return true;
            }
            return true;
        }

    

        if (!sender.hasPermission("pixelticket.admin")) { sender.sendMessage(color("&c권한이 없습니다.")); return true; }
        if (args.length == 0) { help(sender); return true; }

        if (args[0].equalsIgnoreCase("테스트")) {
            sender.sendMessage(color("&a[SELFTEST] &7플러그인 로드/명령/이벤트 등록 OK."));
            return true;
        }

        if (args[0].equalsIgnoreCase("설정")) {
            if (!(sender instanceof Player)) { sender.sendMessage(color("&c플레이어만 사용 가능합니다.")); return true; }
            if (args.length < 2) { sender.sendMessage(color("&e사용법: /지급 설정 <권종류>")); return true; }
            int consumedT = 1;
            TicketType t = TicketType.fromKorean(args[1]);
            if (t == null && args.length>=3) { t = TicketType.fromKorean(args[1]+args[2]); if (t!=null) consumedT=2; }
            if (t == null && args.length>=4) { TicketType t3 = TicketType.fromKorean(args[1]+args[2]+args[3]); if (t3!=null){ t=t3; consumedT=3; } }
            if (t == null) { sender.sendMessage(color("&c권종류를 찾을 수 없습니다.")); return true; }
            Player p = (Player) sender;
            ItemStack hand = p.getInventory().getItemInMainHand();
        // Legacy ticket notice (no event to cancel here)
        if (hand != null && hand.getType()!=Material.AIR && isLegacyTicket(hand)) {
            String msg = getConfig().getString("voucher.legacy_message", "&c구버전 소모권은 비활성화되었습니다. 교환소에서 신버전으로 교환하세요.");
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            return true;
}
        
            if (hand == null || hand.getType() == Material.AIR) { p.sendMessage(color("&c손에 든 아이템이 없습니다.")); return true; }
            markAsTicket(hand, t);
            p.sendMessage(color("&a설정 완료: &f") + t.displayName + color("&7 (손에 든 아이템이 해당 권으로 설정됨)"));
            return true;
        }

        int consumed = 1;
TicketType t = TicketType.fromKorean(args.length>0?args[0]:null);
if (t == null && args.length>=2) { t = TicketType.fromKorean(args[0]+args[1]); if (t!=null) consumed=2; }
if (t == null && args.length>=3) { TicketType t3 = TicketType.fromKorean(args[0]+args[1]+args[2]); if (t3!=null){ t=t3; consumed=3; } }
if (t == null) { sender.sendMessage(color("&c권종류를 찾을 수 없습니다.")); return true; }
if (args.length < consumed + 2) { help(sender); return true; }
Player target = Bukkit.getPlayerExact(args[consumed]);
if (target == null) { sender.sendMessage(color("&c해당 플레이어를 찾을 수 없습니다: &f")+args[consumed]); return true; }
String customName = String.join(" ", java.util.Arrays.copyOfRange(args, consumed+1, args.length));
org.bukkit.inventory.ItemStack ticket = createTicket(t, 1, customName);
target.getInventory().addItem(ticket);
sender.sendMessage(color("&a지급 완료: &f")+t.displayName+color(" &7→ &f")+target.getName());
target.sendMessage(color("&d[권 지급] &f")+t.displayName+color(" &7 1개를 받았습니다."));
return true;
    }

    private void help(CommandSender s){
        s.sendMessage(color("&b/지급 <권종류> <플레이어> <이름> &7- 표시이름(§6[ 소모권 ] §f<이름>)"));
        String kinds = java.util.Arrays.stream(TicketType.values())
                .map(t -> t.displayName)
                .collect(java.util.stream.Collectors.joining(", "));
        s.sendMessage(color("&7권종류: &f") + kinds);
        s.sendMessage(color("&b/지급 설정 <권종류> &7- 손에 든 아이템을 해당 권으로 태그"));
        s.sendMessage(color("&b/지급 테스트 &7- 플러그인 자체 점검"));
    }

    
@Override
public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
    if (!cmd.getName().equalsIgnoreCase("지급")) return java.util.Collections.emptyList();
    java.util.List<String> out = new java.util.ArrayList<>();

    // 1) 첫 인자: '설정', '테스트' 또는 권종류 표시명
    if (args.length == 1) {
        out.add("설정");
        out.add("테스트");
        for (TicketType t : TicketType.values()) out.add(t.displayName);
        return out.stream().filter(s -> s.startsWith(args[0])).collect(java.util.stream.Collectors.toList());
    }

    // 2) 설정 브랜치: /지급 설정 <권종류> [...]  -> 권종류 조합 지원
    if ("설정".equalsIgnoreCase(args[0])) {
        if (args.length >= 2 && args.length <= 4) {
            String probe = String.join("", java.util.Arrays.copyOfRange(args, 1, args.length));
            for (TicketType t : TicketType.values()) {
                String key = t.displayName.replace(" ","").replace("(","").replace(")","");
                if (key.startsWith(probe.replace(" ",""))) out.add(t.displayName);
            }
            return out;
        }
        return java.util.Collections.emptyList();
    }

    // 3) 일반 지급: /지급 <권종류> <닉> <표시이름...>
    // 권종류 토큰을 1~3개까지 결합하여 식별
    int consumed = 1;
    TicketType t = TicketType.fromKorean(args[0]);
    if (t == null && args.length >= 2) { t = TicketType.fromKorean(args[0] + args[1]); if (t != null) consumed = 2; }
    if (t == null && args.length >= 3) { TicketType t3 = TicketType.fromKorean(args[0] + args[1] + args[2]); if (t3 != null) { t = t3; consumed = 3; } }

    if (t == null) {
        // 아직 권종류 미완성 → 권종류 후보 제시
        String probe = String.join("", java.util.Arrays.copyOfRange(args, 0, Math.min(args.length, 3)));
        for (TicketType tt : TicketType.values()) {
            String key = tt.displayName.replace(" ","").replace("(","").replace(")","");
            if (key.startsWith(probe.replace(" ",""))) out.add(tt.displayName);
        }
        return out;
    }

    // 닉네임 위치 자동 완성
    if (args.length == consumed + 1) {
        String prefix = args[consumed];
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name.toLowerCase().startsWith(prefix.toLowerCase())) out.add(name);
        }
        return out;
    }

    return java.util.Collections.emptyList();
}
@EventHandler

    public void onUse(PlayerInteractEvent e){
        // block new uses while any pending input is waiting
        if (pending.containsKey(e.getPlayer().getUniqueId()) || natureSlotWaiting.containsKey(e.getPlayer().getUniqueId()) || heartSlotWaiting.containsKey(e.getPlayer().getUniqueId()) || ivLockWaiting.containsKey(e.getPlayer().getUniqueId())) { e.setCancelled(true); e.getPlayer().sendMessage(color("&c진행 중인 작업이 있습니다. 채팅 입력 또는 취소 명령을 먼저 완료하세요.")); return; }

        if (e.getHand() != EquipmentSlot.HAND) return; // 보조손 중복 방지
        if (e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        // dedupe per player
        long now = System.currentTimeMillis();
        long last = lastUseMs.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < USE_COOLDOWN_MS) return;
        lastUseMs.put(p.getUniqueId(), now);
        ItemStack hand = p.getInventory().getItemInMainHand();
        // Legacy ticket notice (no event to cancel here)
        if (hand != null && hand.getType()!=Material.AIR && isLegacyTicket(hand)) {
            String msg = getConfig().getString("voucher.legacy_message", "&c구버전 소모권은 비활성화되었습니다. 교환소에서 신버전으로 교환하세요.");
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
    // === Heart Scale use start ===
    if (hand != null && hand.getType() != Material.AIR && isHeart(hand)) {
        e.setCancelled(true);
        java.util.UUID u = p.getUniqueId();
        if (heartSlotWaiting.containsKey(u)) {
            p.sendMessage(color("&c이미 진행 중입니다. 채팅에 슬롯(1~6)을 입력하거나 &e/하트비늘 취소&c 를 사용하세요."));
            return;
        }
        consumeOneHeart(p); // keep refund for /하트비늘 취소
        heartSlotWaiting.put(u, -1);
        p.sendMessage(color("&d[하트비늘] &f대상 &e슬롯(1~6)&f을 채팅으로 입력하세요. &7(취소: &e/하트비늘 취소&7)"));
        return;
    }
    // === Heart Scale use end ===

        
        if (!isTicket(hand)) return;
        try { normalizeVoucherMeta(hand, getType(hand), null); } catch (Throwable ignored) {}
        // (cancel removed in non-event context)
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
                consumeOne(p);
                openLegendGui(p, 0);
                break;

            case SHINY: case BIGGEST: case SMALLEST: case NEUTER:
            case NATURE_CHANGE:
            case RANDOM_IVS:
            case IV_LOCK_RANDOM: case IV_LOCK_MAX:
            case GENDER_MALE: case GENDER_FEMALE:
            case V1: case V2: case V3: case V4: case V5: case V6:
            case NATURE_FIX: case ABILITY_PATCH:
            case LEG_FORCE_SPAWN:
                if (type == TicketType.LEG_FORCE_SPAWN) {
                    consumeOne(p);
                    runConsole("spawnlegendary " + p.getName());
                } else {
                    org.bukkit.inventory.ItemStack _ref = null;
                    try {
                        org.bukkit.inventory.ItemStack _h = p.getInventory().getItemInMainHand();
                        if (_h != null && _h.getType() != org.bukkit.Material.AIR) { _ref = _h.clone(); _ref.setAmount(1); }
                    } catch (Throwable ignore) {}
                    if (_ref != null) voucherRefund.put(p.getUniqueId(), _ref);
                    consumeOne(p);
                    askSlotThen(p, type);
                }
                break;
        }
}

    private String pickRandomLegend(){
        List<String> legends = legendsCfg.getStringList("legendaries");
        if (legends==null || legends.isEmpty()) return "Mewtwo";
        return legends.get(new java.util.Random().nextInt(legends.size()));
    }

    private void askSlotThen(Player p, TicketType type){
        p.sendMessage(color("&a권이 즉시 소모되었습니다. &f1~6 &a중 하나를 선택하세요."));
        try {
            net.md_5.bungee.api.chat.TextComponent base = new net.md_5.bungee.api.chat.TextComponent(color("&7[슬롯 선택] "));
            for (int iBtn=1;iBtn<=6;iBtn++){
                net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent("["+iBtn+"] ");
                btn.setBold(true);
                btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, String.valueOf(iBtn)));
                btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.ComponentBuilder(color("&e클릭해서 채팅창에 입력")).create()));
                base.addExtra(btn);
            }
            // 취소 버튼 추가
            net.md_5.bungee.api.chat.TextComponent cancel = new net.md_5.bungee.api.chat.TextComponent(color("&c[취소]"));
            cancel.setBold(true);
            cancel.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, "취소"));
            cancel.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder(color("&e클릭하면 취소합니다")).create()));
            base.addExtra(cancel);
            p.spigot().sendMessage(base);
        } catch (Throwable ignored) {}

        pending.put(p.getUniqueId(), new PendingAction(type));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        if (heartSlotWaiting.containsKey(u) || natureSlotWaiting.containsKey(u) || pending.containsKey(u)) e.setCancelled(true);
        if (heartSlotWaiting.containsKey(u) || natureSlotWaiting.containsKey(u) || pending.containsKey(u)) {
            e.setCancelled(true);
        }
        
        if (heartSlotWaiting.containsKey(p.getUniqueId())){
            String raw = e.getMessage();
            String msg = ChatColor.stripColor(raw).trim();
            if (msg.equalsIgnoreCase("취소")){
                ItemStack refund = heartRefund.remove(p.getUniqueId());
                if (refund!=null) p.getInventory().addItem(refund);
                heartSlotWaiting.remove(p.getUniqueId());
                p.sendMessage(color("&7작업을 취소했습니다."));
                return;
            }
            int st = heartSlotWaiting.get(p.getUniqueId()); // -1: need party slot; >=100: need index; >=200: need #move; 1..6: need #move (teach)
            try{
                if (st == -1){
                    String digitOnly = msg.replaceAll("[^0-9]", "");
                    int slot = Integer.parseInt(digitOnly);
                    if (slot<1 || slot>6){ p.sendMessage(color("&c1~6 사이의 숫자를 입력하세요.")); return; }
                    if (hasPBLite()){
                        heartSlotWaiting.put(p.getUniqueId(), 100 + slot); // expect index next
                        p.sendMessage(color("&f변경할 기술 칸 &e번호(1~4)&f를 입력하세요."));
                    } else {
                        heartSlotWaiting.put(p.getUniqueId(), slot); // expect #move next (teach)
                        p.sendMessage(color("&f이제 &e#기술이름(한글/영문)&f을 입력하세요. 예: #Thunderbolt"));
                    }
                    return;
                }
                // If expecting index (PBLite path)
                if (st >= 100 && st < 200 && !msg.startsWith("#")){
                    String d = msg.replaceAll("[^0-9]", "");
                    int idx = Integer.parseInt(d);
                    if (idx<1 || idx>4){ p.sendMessage(color("&c1~4 사이의 숫자를 입력하세요.")); return; }
                    int slot = st - 100;
                    heartSlotWaiting.put(p.getUniqueId(), 200 + (slot*10 + idx)); // expect #move
                    p.sendMessage(color("&7이제 &e#기술이름(한글/영문)&f을 입력하세요. 예: #Flamethrower"));
                    return;
                }
                // If expecting #move now
                if (!msg.startsWith("#")){ p.sendMessage(color("&c#기술이름 형식으로 입력하세요. 예: #Thunderbolt")); return; }
                String move = translateMove(msg.substring(1));
                if (move.isEmpty()){ p.sendMessage(color("&c기술명(한글/영문)을 입력하세요. 예: #Thunderbolt")); return; }

                if (st >= 200){
                    int code = st - 200;
                    int slot = code / 10;
                    int idx  = code % 10;
                    // PBLite command (exact slot index replacement)
                    tryCommands("pr setmove "+p.getName()+" "+slot+" "+idx+" "+move);
                    heartSlotWaiting.remove(p.getUniqueId());
                    heartRefund.remove(p.getUniqueId());
                    p.sendMessage(color("&a기술을 변경했습니다: &f슬롯 "+slot+" / 칸 "+idx+" → &e"+move));
                    return;
                } else if (st >= 1 && st <= 6){
                    int slot = st;
                    // Fallback: teach (Pixelmon)
                    tryCommands("teach "+p.getName()+" "+slot+" "+move);
                    heartSlotWaiting.remove(p.getUniqueId());
                    heartRefund.remove(p.getUniqueId());
                    p.sendMessage(color("&a기술을 가르쳤습니다."));
                    return;
                }
            }catch(NumberFormatException ex){
                p.sendMessage(color("&c숫자를 입력하세요."));
                return;
            }
        }

        
        // 성격 확정권: #성격 입력 대기 처리
        if (natureSlotWaiting.containsKey(p.getUniqueId())){
            String raw = e.getMessage();
            String msg = ChatColor.stripColor(raw).trim();
            if (msg.equalsIgnoreCase("취소")){
                natureSlotWaiting.remove(p.getUniqueId());
                org.bukkit.inventory.ItemStack _refund = voucherRefund.remove(p.getUniqueId());
                if (_refund != null) p.getInventory().addItem(_refund);
                p.sendMessage(color("&7작업을 취소하고 &a소모권을 반환했습니다."));
                e.setCancelled(true);
                return;
            }
            if (!msg.startsWith("#")){
                p.sendMessage(color("&c#성격 형식으로 입력하세요. 예: #고집 또는 #adamant"));
                return;
            }
            String natKey = msg.substring(1).toLowerCase().replace(" ", "").replace("-", "");
            String nat = natureAlias.getOrDefault(natKey, natKey);
            int slot = natureSlotWaiting.remove(p.getUniqueId());
            // 허용 목록(영문) 검사 - 잘못된 입력 방지
            String[] allowed = new String[]{"adamant","bashful","bold","brave","calm","careful","docile","gentle","hardy","hasty","impish","jolly","lax","lonely","mild","modest","naive","naughty","quiet","quirky","rash","relaxed","sassy","serious","timid"};
            java.util.Set<String> allowSet = new java.util.HashSet<>(java.util.Arrays.asList(allowed));
            if (!allowSet.contains(nat)){
                p.sendMessage(color("&c알 수 없는 성격입니다: &f"+natKey+" &7(moves_ko.yml과 유사하게 &fnatures_ko.yml&7에 별칭을 추가할 수 있습니다)"));
                return;
            }
            tryCommands(
                    "pokeedit "+p.getName()+" "+slot+" n:"+nat,
                    "pokeedit "+p.getName()+" "+slot+" nature:"+nat
            );
            p.sendMessage(color("&d[성격변경권(확정)] &f슬롯 "+slot+" 성격을 &d"+nat+" &f로 변경 시도."));
            return;
        }

        // 개체값고정 스탯 선택 대기 처리
        if (ivLockWaiting.containsKey(p.getUniqueId())){
            String msg = ChatColor.stripColor(e.getMessage().trim());
            if (msg.equalsIgnoreCase("취소")){
                ivLockWaiting.remove(p.getUniqueId());
                org.bukkit.inventory.ItemStack _refund = voucherRefund.remove(p.getUniqueId());
                if (_refund != null) p.getInventory().addItem(_refund);
                p.sendMessage(color("&7작업을 취소하고 &a소모권을 반환했습니다."));
                e.setCancelled(true);
                return;
            }
            IvPending ivp = ivLockWaiting.get(p.getUniqueId());
            // 차단 대상 재검사(메타몽/오롱털/무성): 스탯 입력 단계에서도 보호
            if (ivp != null && (isDittoSlot(p, ivp.slot) || isGrimmsnarlSlot(p, ivp.slot) || isGenderlessSlot(p, ivp.slot))) {
                ivLockWaiting.remove(p.getUniqueId());
                org.bukkit.inventory.ItemStack exact2 = voucherRefund.remove(p.getUniqueId());
                if (exact2 != null) {
                    p.getInventory().addItem(exact2);
                } else {
                    try {
                        org.bukkit.inventory.ItemStack refund3 = createTicket(ivp.type, 1);
                        p.getInventory().addItem(refund3);
                    } catch (Throwable ignored) {}
                }
                p.sendMessage(color("&c[개체값고정권] 해당 슬롯은 사용 불가 대상입니다. (메타몽/오롱털/무성)"));
                e.setCancelled(true);
                return;
            }

            String key = null; String pretty = null;
            if (msg.equalsIgnoreCase("체력")){ key = "ivhp"; pretty="체력"; }
            else if (msg.equalsIgnoreCase("공격")){ key = "ivatk"; pretty="공격"; }
            else if (msg.equalsIgnoreCase("방어")){ key = "ivdef"; pretty="방어"; }
            else if (msg.equalsIgnoreCase("특수공격")){ key = "ivspa"; pretty="특수공격"; }
            else if (msg.equalsIgnoreCase("특수방어")){ key = "ivspd"; pretty="특수방어"; }
            else if (msg.equalsIgnoreCase("스피드")){ key = "ivspe"; pretty="스피드"; }
            if (key == null){
                p.sendMessage(color("&c[안내] 체력/공격/방어/특수공격/특수방어/스피드 중에서 선택하세요."));
                e.setCancelled(true);
                return;
            }
            int val = 31;
            if (ivp.type == TicketType.IV_LOCK_RANDOM){
                java.util.Random r = new java.util.Random();
                val = r.nextInt(32);
            }
            // Try multiple key variants for speed
            tryCommands(String.format("pokeedit %s %d %s:%d", p.getName(), ivp.slot, key, val));
            if (key.equals("ivspe")){
                tryCommands(String.format("pokeedit %s %d ivspeed:%d", p.getName(), ivp.slot, val));
            }
            p.sendMessage(color((ivp.type==TicketType.IV_LOCK_MAX? "&6[개체값고정최대] ":"&6[개체값고정랜덤] ")
                    + "&f슬롯 "+ivp.slot+" &e"+pretty+"&f만 " + (ivp.type==TicketType.IV_LOCK_MAX? "31":"랜덤("+val+")") + " 으로 변경 시도."));
            ivLockWaiting.remove(p.getUniqueId());
            e.setCancelled(true);
            return;
        }
if (!pending.containsKey(u)) return;
        String rawMsg = org.bukkit.ChatColor.stripColor(e.getMessage()).trim();
        if (rawMsg.equalsIgnoreCase("취소")){
            pending.remove(u);
            org.bukkit.inventory.ItemStack _refund = voucherRefund.remove(u);
            if (_refund != null) p.getInventory().addItem(_refund);
            p.sendMessage(color("&7작업을 취소하고 &a소모권을 반환했습니다."));
            return;
        }
        int slot;
        String digitOnly = rawMsg.replaceAll("[^0-9]", "");
        try { slot = Integer.parseInt(digitOnly); } catch (Exception ex) { e.getPlayer().sendMessage(color("&c숫자(1~6)를 입력하세요.")); return; }
        if (slot<1 || slot>6) { e.getPlayer().sendMessage(color("&c슬롯은 1~6 범위여야 합니다.")); return; }
        PendingAction pa = pending.remove(u);
        final Player fp = e.getPlayer();
        final PendingAction fpa = pa;
        final int fslot = slot;
        new BukkitRunnable(){ @Override public void run(){ handleSlotAction(fp, fpa.type, fslot); } }.runTask(this);
    }

    
    private boolean hasPBLite(){
        try {
            org.bukkit.plugin.Plugin p = getServer().getPluginManager().getPlugin("PokebuilderLite");
            return p != null && p.isEnabled();
        } catch (Throwable t){ return false; }
    }
    
    private void handleSlotAction(Player p, TicketType type, int slot){
        // Block certain targets for V1~V6 and refund
        if (type==TicketType.V1 || type==TicketType.V2 || type==TicketType.V3 ||
            type==TicketType.V4 || type==TicketType.V5 || type==TicketType.V6) {
            boolean blocked = isDittoSlot(p, slot) || isGrimmsnarlSlot(p, slot) || isGenderlessSlot(p, slot);
            if (blocked) {
                p.sendMessage(color("&c[확정권] 해당 슬롯은 사용 불가 대상입니다. (메타몽/오롱털/무성)"));
                try {
                    org.bukkit.inventory.ItemStack refund = createTicket(type, 1);
                    java.util.Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(refund);
                    if (left != null && !left.isEmpty()) {
                        for (org.bukkit.inventory.ItemStack remain : left.values()) {
                            if (remain == null) continue;
                            p.getWorld().dropItemNaturally(p.getLocation(), remain);
                        }
                    }
                } catch (Throwable ignored) {}
                return;
            }
        }

        // Ditto protection for some tickets
        if (type==TicketType.RANDOM_IVS || type==TicketType.V1 || type==TicketType.V2 || type==TicketType.V3 ||
            type==TicketType.V4 || type==TicketType.V5 || type==TicketType.V6) {
            if (isDittoSlot(p, slot)) {
                p.sendMessage(color("&c[안내] 메타몽(Ditto)에는 이 변경권을 사용할 수 없습니다."));
                return;
            }
        }

        switch (type){
            case IV_LOCK_RANDOM:
            case IV_LOCK_MAX: {
                // 차단 대상(메타몽/오롱털/무성) 보호: 즉시 환불 후 종료
                if (isDittoSlot(p, slot) || isGrimmsnarlSlot(p, slot) || isGenderlessSlot(p, slot)) {
                    org.bukkit.inventory.ItemStack exact = voucherRefund.remove(p.getUniqueId());
                    if (exact != null) {
                        p.getInventory().addItem(exact);
                    } else {
                        try {
                            org.bukkit.inventory.ItemStack refund2 = createTicket(type, 1);
                            java.util.Map<Integer, org.bukkit.inventory.ItemStack> left2 = p.getInventory().addItem(refund2);
                            if (left2 != null && !left2.isEmpty()) {
                                for (org.bukkit.inventory.ItemStack remain : left2.values()) {
                                    if (remain == null) continue;
                                    p.getWorld().dropItemNaturally(p.getLocation(), remain);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                    p.sendMessage(color("&c[개체값고정권] 해당 슬롯은 사용 불가 대상입니다. (메타몽/오롱털/무성)"));
                    return;
                }

                try {
                    net.md_5.bungee.api.chat.TextComponent base = new net.md_5.bungee.api.chat.TextComponent(color("&7[스탯 선택] "));
                    String[] labels = new String[]{"체력","공격","방어","특수공격","특수방어","스피드"};
                    for (String lab : labels){
                        net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent("["+lab+"] ");
                        btn.setBold(true);
                        btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, lab));
                        btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.ComponentBuilder(color("&e클릭해서 채팅창에 입력")).create()));
                        base.addExtra(btn);
                    }
                    p.spigot().sendMessage(base);
                } catch (Throwable ignored) {}
                ivLockWaiting.put(p.getUniqueId(), new IvPending(type, slot));
                p.sendMessage(color("&f원하는 스탯 이름을 클릭하거나 입력하세요. &7(취소: 취소)"));
                return;
            }
            case SHINY:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" shiny",
                        "pokeedit "+p.getName()+" "+slot+" shiny:true",
                        "pokeedit "+p.getName()+" "+slot+" s:1"
                );
            p.sendMessage(color("&e[이로치권] &f슬롯 "+slot+" 포켓몬을 이로치로 변경 시도."));
                break;

            case NATURE_CHANGE: {
                String[] natures = new String[]{"adamant","bashful","bold","brave","calm","careful","docile","gentle","hardy","hasty","impish","jolly","lax","lonely","mild","modest","naive","naughty","quiet","quirky","rash","relaxed","sassy","serious","timid"};
                String nat = natures[new java.util.Random().nextInt(natures.length)];
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" n:"+nat,
                        "pokeedit "+p.getName()+" "+slot+" nature:"+nat
                );
            p.sendMessage(color("&d[성격변경권] &f슬롯 " + slot + " 성격을 &d" + nat + " &f로 변경 시도."));
                break;
            }
            case NATURE_FIX: {
                natureSlotWaiting.put(p.getUniqueId(), slot);
                p.sendMessage(color("&d[성격변경권(확정)] &f이제 &e#성격(한글/영문)&f을 입력하세요. 예: &e#고집 &7또는 &e#adamant"));
                return;
            }
            case ABILITY_PATCH:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" ha untradeable unbreedable",
                        "pokeedit "+p.getName()+" "+slot+" hiddenability:true untradeable unbreedable",
                        "pokeedit "+p.getName()+" "+slot+" ha:true untradeable unbreedable"
                );
            p.sendMessage(color("&b[특성패치] &f슬롯 "+slot+" &d드림특성(히든)&f 적용 + &7교환/교배 불가 설정 시도."));
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

            case RANDOM_IVS: {
                java.util.Random r = new java.util.Random();
                int hp=r.nextInt(32), atk=r.nextInt(32), def=r.nextInt(32), spa=r.nextInt(32), spd=r.nextInt(32), spe=r.nextInt(32);
                tryCommands(
                        String.format("pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspd:%d", p.getName(), slot, hp, atk, def, spa, spd, spe),
                        String.format("pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspeed:%d", p.getName(), slot, hp, atk, def, spa, spd, spe)
                );
            p.sendMessage(color("&6[랜덤개체값권] &f슬롯 "+slot+" IV 전부 랜덤화 시도."));
                break;
            }

            case GENDER_MALE:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gender:male",
                        "pokeedit "+p.getName()+" "+slot+" g:male",
                        "pokeedit "+p.getName()+" "+slot+" sex:male"
                );
            p.sendMessage(color("&b[성별변경권] &f슬롯 "+slot+" 수컷으로 변경 시도."));
                break;

            case GENDER_FEMALE:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gender:female",
                        "pokeedit "+p.getName()+" "+slot+" g:female",
                        "pokeedit "+p.getName()+" "+slot+" sex:female"
                );
            p.sendMessage(color("&b[성별변경권] &f슬롯 "+slot+" 암컷으로 변경 시도."));
                break;

            case V1: case V2: case V3: case V4: case V5: case V6:
                int n = Integer.parseInt(type.name().substring(1));
                applyVIV(p, slot, n);
                break;

            case LEG_FORCE_SPAWN:
            case LEG_SELECT:
            case LEG_RANDOM:
                break;
        }
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

    private void tryCommands(String... commands){
        for (String c : commands) {
            try {
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                if (ok) { getLogger().info("[PixelTicket] Ran: "+c); break; }
            } catch (Throwable ex) { getLogger().warning("[PixelTicket] Command failed: "+c+" | "+ex.getMessage()); }
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
            ItemStack it = new ItemStack(Material.NETHER_STAR);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(color("&b") + localize(name));
            m.setLore(java.util.Arrays.asList(
                    color("&7클릭 시 지급: &f")+localize(name),
                    color("&8영문: &7")+name,
                    color("&8/pokegive ")+p.getName()+" "+name
            ));
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
        m.setDisplayName(color(name));
        it.setItemMeta(m);
        return it;
    }

    static class LegendHolder implements InventoryHolder { 
        final int page; 
        LegendHolder(int page){ this.page=page; } 
        @Override public Inventory getInventory(){ return null; } 
    }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        if (!(e.getInventory().getHolder() instanceof LegendHolder)) return;
        // (cancel removed in non-event context)
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
            String eng = null;
            java.util.List<String> lore = clicked.getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    String s = ChatColor.stripColor(line);
                    if (s.startsWith("영문: ")) {
                        eng = s.substring("영문: ".length()).trim();
                    }
                }
            }
            if (eng == null || eng.isEmpty()) {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                eng = name;
            }
            runConsole("pokegive "+p.getName()+" "+eng);
            p.sendMessage(color("&b[전설선택권] &f지급 완료: &b")+localize(eng));
            p.closeInventory();
        }
    }


    private 
    boolean isGrimmsnarlSlot(Player p, int slot) {
        // 오롱털(Grimmsnarl) 감지
        try {
            Class<?> proxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            java.lang.reflect.Method getParty = proxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;
            java.lang.reflect.Method getMethod = party.getClass().getMethod("get", int.class);
            Object pokemon = getMethod.invoke(party, slot - 1);
            if (pokemon == null) return false;
            try {
                java.lang.reflect.Method getSpecies = pokemon.getClass().getMethod("getSpecies");
                Object species = getSpecies.invoke(pokemon);
                if (species != null) {
                    String name;
                    try {
                        java.lang.reflect.Method nameMethod = species.getClass().getMethod("name");
                        name = String.valueOf(nameMethod.invoke(species));
                    } catch (NoSuchMethodException ex) {
                        try {
                            java.lang.reflect.Method getName = species.getClass().getMethod("getName");
                            Object n = getName.invoke(species);
                            name = String.valueOf(n);
                        } catch (NoSuchMethodException ex2) {
                            name = String.valueOf(species);
                        }
                    }
                    if (name == null) return false;
                    String low = name.toLowerCase();
                    return low.contains("grimmsnarl") || name.contains("오롱털");
                }
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    boolean isGenderlessSlot(Player p, int slot) {
        // Pixelmon Gender.GENDERLESS 감지 (가능하면)
        try {
            Class<?> proxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            java.lang.reflect.Method getParty = proxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;
            java.lang.reflect.Method getMethod = party.getClass().getMethod("get", int.class);
            Object pokemon = getMethod.invoke(party, slot - 1);
            if (pokemon == null) return false;
            try {
                java.lang.reflect.Method getGender = pokemon.getClass().getMethod("getGender");
                Object gender = getGender.invoke(pokemon);
                if (gender != null) {
                    String g;
                    try {
                        java.lang.reflect.Method nameMethod = gender.getClass().getMethod("name");
                        g = String.valueOf(nameMethod.invoke(gender));
                    } catch (NoSuchMethodException ex) {
                        g = String.valueOf(gender);
                    }
                    if (g == null) return false;
                    String low = g.toLowerCase();
                    // Pixelmon enum likely "GENDERLESS"
                    return low.contains("genderless") || g.contains("무성");
                }
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            return false;
        }
        return false;
    }


boolean isDittoSlot(Player p, int slot) {
        try {
            Class<?> proxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            java.lang.reflect.Method getParty = proxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;
            java.lang.reflect.Method getMethod = party.getClass().getMethod("get", int.class);
            Object pokemon = getMethod.invoke(party, slot - 1);
            if (pokemon == null) return false;
            // Try getSpecies().name() or getSpecies().getName()
            try {
                java.lang.reflect.Method getSpecies = pokemon.getClass().getMethod("getSpecies");
                Object species = getSpecies.invoke(pokemon);
                if (species != null) {
                    String name;
                    try {
                        java.lang.reflect.Method nameMethod = species.getClass().getMethod("name");
                        name = String.valueOf(nameMethod.invoke(species));
                    } catch (NoSuchMethodException ex) {
                        try {
                            java.lang.reflect.Method getName = species.getClass().getMethod("getName");
                            Object n = getName.invoke(species);
                            name = String.valueOf(n);
                        } catch (NoSuchMethodException ex2) {
                            name = String.valueOf(species);
                        }
                    }
                    return name != null && name.toLowerCase().contains("ditto");
                }
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            // Pixelmon API not available – do not block
            return false;
        }
        return false;
    }

    public String color(String s){ return ChatColor.translateAlternateColorCodes('&', s); }

    // ===== Heart Scale (하트비늘) =====
    private String heartName = "&d하트비늘";
    private java.util.List<String> heartLore = new java.util.ArrayList<>();
    private java.util.Map<java.util.UUID, Integer> heartSlotWaiting = new java.util.HashMap<>();
    private java.util.Map<java.util.UUID, ItemStack> heartRefund = new java.util.HashMap<>();

    
    private boolean isHeart(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(HEART_KEY, org.bukkit.persistence.PersistentDataType.BYTE)) return false;
        Integer ver = pdc.get(HEART_VER, org.bukkit.persistence.PersistentDataType.INTEGER);
        return ver != null && ver == HEART_VERSION;
    }


    private void consumeOneHeart(Player p){
        ItemStack hand = p.getInventory().getItemInMainHand();
        // Legacy ticket notice (no event to cancel here)
        if (hand != null && hand.getType()!=Material.AIR && isLegacyTicket(hand)) {
            String msg = getConfig().getString("voucher.legacy_message", "&c구버전 소모권은 비활성화되었습니다. 교환소에서 신버전으로 교환하세요.");
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        
        if (hand==null) return;
        ItemStack refund = hand.clone(); refund.setAmount(1);
        int amt = hand.getAmount();
        if (amt<=1) p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else { hand.setAmount(amt-1); p.getInventory().setItemInMainHand(hand); }
        p.updateInventory();
        heartRefund.put(p.getUniqueId(), refund);
    }



public String getHeartNameSafe(){
    try { return this.heartName == null ? "&d하트비늘" : this.heartName; }
    catch (Throwable t){ return "&d하트비늘"; }
}




    // -- reinserted alias block --

    // === Heart Template Persistence ===
    private java.io.File heartTplFile;
    private void ensureHeartTplFile(){
        if (heartTplFile == null) heartTplFile = new java.io.File(getDataFolder(), "heart_template.yml");
    }
    private void saveHeartTemplateFrom(org.bukkit.inventory.ItemStack src){
        ensureHeartTplFile();
        try{
            org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
            y.set("material", src.getType().name());
            org.bukkit.inventory.meta.ItemMeta m = src.getItemMeta();
            if (m != null && m.hasDisplayName()) y.set("name", m.getDisplayName());
            java.util.List<String> lore = (m!=null && m.hasLore()) ? m.getLore() : null;
            if (lore!=null) y.set("lore", lore);
            y.save(heartTplFile);
        }catch(Exception ex){ getLogger().warning("[PixelTicket] heart_template.yml 저장 실패: "+ex.getMessage()); }
    }
    private org.bukkit.inventory.ItemStack createHeartFromTemplate(int amount){
        ensureHeartTplFile();
        org.bukkit.inventory.ItemStack it;
        org.bukkit.configuration.file.YamlConfiguration y = null;
        if (heartTplFile.exists()){
            y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(heartTplFile);
        }
        String mat = y!=null ? y.getString("material", "PRISMARINE_CRYSTALS") : "PRISMARINE_CRYSTALS";
        it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.matchMaterial(mat)!=null? org.bukkit.Material.matchMaterial(mat): org.bukkit.Material.PRISMARINE_CRYSTALS);
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        String name = y!=null ? y.getString("name", color("&d하트비늘")) : color("&d하트비늘");
        java.util.List<String> lore = y!=null && y.isList("lore") ? y.getStringList("lore") : java.util.Arrays.asList(color("&7기술 교습 아이템"), color("&8우클릭 후 가이드에 따르세요"));
        meta.setDisplayName(name);
        meta.setLore(lore);
        try{
            meta.getPersistentDataContainer().set(HEART_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            meta.getPersistentDataContainer().set(HEART_VER, org.bukkit.persistence.PersistentDataType.INTEGER, HEART_VERSION);
        }catch(Throwable ignored){}
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, amount));
        return it;
    }

    private void loadMoveAliases(){
    try {
        java.io.File dataDir = getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        java.io.File f = new java.io.File(dataDir, "moves_ko.yml");
        org.bukkit.configuration.file.YamlConfiguration y;
        if (!f.exists()){
            y = new org.bukkit.configuration.file.YamlConfiguration();
            // defaults
            y.set("번개", "Thunderbolt");
            y.set("10만볼트", "Thunderbolt");
            y.set("화염방사", "Flamethrower");
            y.set("불대문자", "Flamethrower");
            y.set("냉동빔", "IceBeam");
            y.set("사이코키네시스", "Psychic");
            y.set("지진", "Earthquake");
            y.set("스톤엣지", "StoneEdge");
            y.set("드래곤클로", "DragonClaw");
            y.set("섀도볼", "ShadowBall");
            y.set("하이드로펌프", "HydroPump");
            y.set("리플렉터", "Reflect");
            y.set("빛의장막", "LightScreen");
            y.set("칼춤", "SwordsDance");
            y.set("용춤", "DragonDance");
            y.set("기합구슬", "FocusBlast");
            y.set("폭포오르기", "Waterfall");
            y.set("야습", "ShadowSneak");
            y.save(f);
        }
        y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        moveAlias.clear();
        for (String k : y.getKeys(false)){
            String v = y.getString(k, "");
            if (v != null && !v.trim().isEmpty()){
                moveAlias.put(k.toLowerCase(java.util.Locale.ROOT).trim(), v.trim());
            }
        }
        getLogger().info("[PixelTicket] Loaded move aliases: " + moveAlias.size());
    } catch (Exception ex){
        getLogger().warning("[PixelTicket] Failed to load moves_ko.yml: " + ex.getMessage());
    }

    }

    
    
private ItemStack createHeartItemFrom(ItemStack base) {
        org.bukkit.Material type = (base != null && base.getType() != org.bukkit.Material.AIR) ? base.getType() : org.bukkit.Material.PAPER;
        int amount = (base != null && base.getAmount() > 0) ? base.getAmount() : 1;
        ItemStack item = new ItemStack(type, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.LIGHT_PURPLE + getHeartNameSafe());
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(org.bukkit.ChatColor.GRAY + "특수 교습권");
            lore.add(org.bukkit.ChatColor.DARK_GRAY + "(우클릭 후 슬롯 또는 #기술명 입력)");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(HEART_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            meta.getPersistentDataContainer().set(HEART_VER, org.bukkit.persistence.PersistentDataType.INTEGER, HEART_VERSION);
            item.setItemMeta(meta);
        }
        return item;
    }

private String translateMove(String raw){
    if (raw == null) return "";
    String m = raw.trim();
    m = org.bukkit.ChatColor.stripColor(m);
    m = m.replace("#", "").trim();
    String key = m.toLowerCase(java.util.Locale.ROOT);
    String mapped = moveAlias.get(key);
    return mapped != null ? mapped : m;
}

    // === Added safe implementations ===
    
    private static final String VOUCHER_PREFIX = "§6[ 소모권 ] §f";

    private void normalizeVoucherMeta(org.bukkit.inventory.ItemStack it, TicketType type, String override){
        if (it == null) return;
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        String base = (override != null && !override.trim().isEmpty())
                ? org.bukkit.ChatColor.stripColor(override.trim())
                : (type != null ? org.bukkit.ChatColor.stripColor(type.displayName) : "권");
        String name = VOUCHER_PREFIX + base;
        try {
            if (!meta.hasDisplayName() || !String.valueOf(meta.getDisplayName()).startsWith(VOUCHER_PREFIX)) {
                meta.setDisplayName(name);
            }
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7픽셀몬 소모권");
            String confPath = "voucher.guide_colors." + (type!=null?type.name():"DEFAULT");
            String colorCode = getConfig().getString(confPath, "&b");
            String guideText = type != null && type.lore1 != null ? org.bukkit.ChatColor.stripColor(type.lore1) : "권 사용 안내";
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', colorCode) + guideText);
            lore.add("§7우클릭 사용 · 채팅 안내에 따르세요");
            meta.setLore(lore);
            it.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }
void markAsTicket(org.bukkit.inventory.ItemStack item, TicketType type){
        if (item == null) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        try {
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (KEY_TAG != null)  pdc.set(KEY_TAG,  org.bukkit.persistence.PersistentDataType.INTEGER, 1);
            if (KEY_TYPE != null) pdc.set(KEY_TYPE, org.bukkit.persistence.PersistentDataType.STRING, type != null ? type.id : "UNKNOWN");
            if (KEY_VER != null)  pdc.set(KEY_VER,  org.bukkit.persistence.PersistentDataType.INTEGER, TICKET_VERSION);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private boolean isTicket(org.bukkit.inventory.ItemStack item){
        if (item == null) return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        try {
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (KEY_TYPE != null) {
                String id = pdc.get(KEY_TYPE, org.bukkit.persistence.PersistentDataType.STRING);
                return id != null && !id.isEmpty();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private TicketType getType(org.bukkit.inventory.ItemStack item){
        if (item == null) return null;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        try {
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (KEY_TYPE != null) {
                String id = pdc.get(KEY_TYPE, org.bukkit.persistence.PersistentDataType.STRING);
                if (id != null){
                    // 우선: 정확한 id 또는 enum name으로 매칭
                    for (TicketType t : TicketType.values()){
                        if ((t.id != null && t.id.equalsIgnoreCase(id)) || t.name().equalsIgnoreCase(id)) return t;
                    }
                    // 호환: 기존 fromKorean도 시도
                    TicketType alt = TicketType.fromKorean(id);
                    if (alt != null) return alt;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    
    private org.bukkit.inventory.ItemStack createTicket(TicketType type, int amount){
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER, Math.max(1, amount));
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();

        String rawName = (type != null && type.displayName != null) ? org.bukkit.ChatColor.stripColor(type.displayName) : "권";
        try { meta.setDisplayName("§6[ 소모권 ] §f" + rawName); } catch (Throwable ignored) {}

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7픽셀몬 소모권");
        String confPath = "voucher.guide_colors." + (type!=null?type.name():"DEFAULT");
        String colorCode = getConfig().getString(confPath, "&b");
        String guideText = type != null && type.lore1 != null ? org.bukkit.ChatColor.stripColor(type.lore1) : "권 사용 안내";
        lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', colorCode) + guideText);
        lore.add("§7우클릭 사용 · 채팅 안내에 따르세요");
        try { meta.setLore(lore); } catch (Throwable ignored) {}
        it.setItemMeta(meta);
        // ensure enchanted glint on paper
        try {
            if (it.getType() != org.bukkit.Material.PAPER) it.setType(org.bukkit.Material.PAPER);
            org.bukkit.inventory.meta.ItemMeta _m = it.getItemMeta();
            if (_m != null) {
                try { _m.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true); } catch (Throwable ignored) {}
                try { _m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); } catch (Throwable ignored) {}
                it.setItemMeta(_m);
            }
        } catch (Throwable ignored) {}
    
        normalizeVoucherMeta(it, type, null);
        markAsTicket(it, type);
        return it;
    }

    ItemStack createTicket(TicketType type, int amount, String displayNameOverride){
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER, Math.max(1, amount));
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();

        String baseName = (displayNameOverride != null && !displayNameOverride.trim().isEmpty())
                ? org.bukkit.ChatColor.stripColor(displayNameOverride.trim())
                : (type != null && type.displayName != null) ? org.bukkit.ChatColor.stripColor(type.displayName) : "권";
        try { meta.setDisplayName("§6[ 소모권 ] §f" + baseName); } catch (Throwable ignored) {}

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7픽셀몬 소모권");
        String confPath = "voucher.guide_colors." + (type!=null?type.name():"DEFAULT");
        String colorCode = getConfig().getString(confPath, "&b");
        String guideText = type != null && type.lore1 != null ? org.bukkit.ChatColor.stripColor(type.lore1) : "권 사용 안내";
        lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', colorCode) + guideText);
        lore.add("§7우클릭 사용 · 채팅 안내에 따르세요");
        try { meta.setLore(lore); } catch (Throwable ignored) {}

        it.setItemMeta(meta);
        // ensure enchanted glint on paper
        try {
            if (it.getType() != org.bukkit.Material.PAPER) it.setType(org.bukkit.Material.PAPER);
            org.bukkit.inventory.meta.ItemMeta _m = it.getItemMeta();
            if (_m != null) {
                try { _m.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true); } catch (Throwable ignored) {}
                try { _m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); } catch (Throwable ignored) {}
                it.setItemMeta(_m);
            }
        } catch (Throwable ignored) {}
    
        normalizeVoucherMeta(it, type, displayNameOverride);
        markAsTicket(it, type);
        return it;
    }
    


    private void consumeOne(org.bukkit.entity.Player p){
        try {
            org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
        // Legacy ticket notice (no event to cancel here)
        if (hand != null && hand.getType()!=Material.AIR && isLegacyTicket(hand)) {
            String msg = getConfig().getString("voucher.legacy_message", "&c구버전 소모권은 비활성화되었습니다. 교환소에서 신버전으로 교환하세요.");
            p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
            if (hand == null || hand.getType() == org.bukkit.Material.AIR) return;
            if (!isTicket(hand)) return;
            int amt = hand.getAmount();
            if (amt <= 1) {
                p.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
            } else {
                hand.setAmount(amt - 1);
            }
        } catch (Throwable ignored) {}
    }


    
    private boolean removeOneMatching(org.bukkit.entity.Player p, java.util.function.Predicate<org.bukkit.inventory.ItemStack> match){
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++){
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            try{
                if (match.test(it)){
                    int amt = it.getAmount();
                    if (amt <= 1) inv.setItem(i, new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
                    else it.setAmount(amt - 1);
                    p.updateInventory();
                    return true;
                }
            } catch (Throwable ignored){}
        }
        return false;
    }
private boolean isLegacyTicket(org.bukkit.inventory.ItemStack item){
        if (item == null) return false;
        try {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            Integer ver = (KEY_VER == null) ? null : pdc.get(KEY_VER, org.bukkit.persistence.PersistentDataType.INTEGER);
            // legacy if it's a ticket but version missing or lower than current
            if (!isTicket(item)) return false;
            return ver == null || ver < TICKET_VERSION;
        } catch (Throwable ignored){}
        return false;
    }


    private void loadNatureAliases(){
        try {
            java.io.File dataDir = getDataFolder();
            if (!dataDir.exists()) dataDir.mkdirs();
            java.io.File f = new java.io.File(dataDir, "natures_ko.yml");
            org.bukkit.configuration.file.YamlConfiguration y;
            if (!f.exists()){
                y = new org.bukkit.configuration.file.YamlConfiguration();
                // 기본 매핑
                y.set("고집", "adamant");
                y.set("명랑", "jolly");
                y.set("겁쟁이", "timid");
                y.set("조심", "modest");
                y.set("대담", "bold");
                y.set("차분", "calm");
                y.set("신중", "careful");
                y.set("장난꾸러기", "impish");
                y.set("무사태평", "relaxed");
                y.set("천진난만", "naive");
                y.set("외로움", "lonely");
                y.set("온순", "docile");
                y.set("개구쟁이", "hasty");
                y.set("용감", "brave");
                y.set("냉정", "quiet");
                y.set("변덕", "quirky");
                y.set("덜렁", "rash");
                y.set("의젓", "serious");
                y.set("온화", "gentle");
                y.set("불끈", "naughty");
                y.set("소심", "bashful");
                y.save(f);
            } else {
                y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            }
            natureAlias.clear();
            for (String k : y.getKeys(false)) {
                String v = y.getString(k);
                if (v!=null) natureAlias.put(k.toLowerCase().replace(" ", "").replace("-",""), v.toLowerCase());
            }
        } catch (Throwable ignored){}
    }

}