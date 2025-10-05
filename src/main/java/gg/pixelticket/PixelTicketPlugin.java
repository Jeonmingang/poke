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


    public enum TicketType {
        LEG_RANDOM("전설랜덤권", ChatColor.LIGHT_PURPLE + "우클릭 즉시 전설 랜덤 지급", "LEG_RANDOM", false),
        LEG_SELECT("전설선택권", ChatColor.AQUA + "GUI에서 전설 선택 지급", "LEG_SELECT", false),
        SHINY("이로치권", ChatColor.YELLOW + "채팅에 슬롯 입력 + 이로치 변경", "SHINY", true),
        BIGGEST("가장큼권", ChatColor.GREEN + "채팅에 슬롯 입력 + 크기 Ginormous", "BIGGEST", true),
        SMALLEST("가장작음권", ChatColor.GREEN + "채팅에 슬롯 입력 + 크기 Microscopic", "SMALLEST", true),
        NEUTER("중성화권", ChatColor.RED + "채팅에 슬롯 입력 + 번식 불가", "NEUTER", true),
        NATURE_CHANGE("성격변경권", ChatColor.LIGHT_PURPLE + "채팅에 슬롯 입력 + 성격 랜덤 변경", "NATURE_CHANGE", true),
        RANDOM_IVS("랜덤개체값권", ChatColor.GOLD + "채팅에 슬롯 입력 + IV 전부 랜덤", "RANDOM_IVS", true),
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

    @Override
    public void onEnable(){
        loadMoveAliases();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("지급")).setExecutor(this);
        Objects.requireNonNull(getCommand("하트비늘")).setExecutor(this);
        Objects.requireNonNull(getCommand("지급")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("하트비늘")).setTabCompleter(this);

        KEY_TYPE = new NamespacedKey(this, "ticket_type");
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

        getLogger().info("PixelTicket enabled (v1.0.4).");
    }

    @Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        
        if (cmd.getName().equalsIgnoreCase("하트비늘")){
            // OP-only: all heartscale commands require admin; item use remains event-driven and open to all.
            if (!sender.hasPermission("pixelticket.admin")) {
                sender.sendMessage(color("&c권한이 없습니다."));
                return true;
            }

            if (args.length > 0 && (args[0].equalsIgnoreCase("리로드") || args[0].equalsIgnoreCase("reload"))){
                if (!sender.hasPermission("pixelticket.admin")) { sender.sendMessage(color("&c권한이 없습니다.")); return true; }
                loadMoveAliases();
                sender.sendMessage(color("&amoves_ko.yml을 다시 불러왔습니다. 항목: &e"+moveAlias.size()));
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
        s.sendMessage(color("&7권종류: &f전설랜덤권, 전설선택권, 이로치권, 가장큼권, 가장작음권, 중성화권, 랜덤개체값권, 전설소환권, 성별변경권(수컷), 성별변경권(암컷), v1, v2, v3, v4, v5, v6"));
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
            return Arrays.asList("하트비늘","랜덤개체값권","강화","설정").stream().filter(s1->s1.startsWith(args[1])).collect(Collectors.toList());
        return Collections.emptyList();
    }
    @EventHandler
    public void onUse(PlayerInteractEvent e){
        if (e.getHand() != EquipmentSlot.HAND) return; // 보조손 중복 방지
        if (e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        // dedupe per player
        long now = System.currentTimeMillis();
        long last = lastUseMs.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < USE_COOLDOWN_MS) return;
        lastUseMs.put(p.getUniqueId(), now);
        ItemStack hand = p.getInventory().getItemInMainHand();
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
                openLegendGui(p, 0);
                break;

            case SHINY: case BIGGEST: case SMALLEST: case NEUTER:
            case NATURE_CHANGE:
            case RANDOM_IVS:
            case GENDER_MALE: case GENDER_FEMALE:
            case V1: case V2: case V3: case V4: case V5: case V6:
            case LEG_FORCE_SPAWN:
                if (type == TicketType.LEG_FORCE_SPAWN) {
                    consumeOne(p);
                    runConsole("spawnlegendary " + p.getName());
                } else {
                    if (type == TicketType.NEUTER) consumeOne(p);
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
        p.sendMessage(color("&a채팅에 &f1~6 &a중 하나의 숫자를 입력하세요. (해당 슬롯 적용)"));
        pending.put(p.getUniqueId(), new PendingAction(type));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        if (heartSlotWaiting.containsKey(u) || pending.containsKey(u)) e.setCancelled(true);
        if (heartSlotWaiting.containsKey(u) || pending.containsKey(u)) {
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
                    int slot = Integer.parseInt(msg);
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
                    int idx = Integer.parseInt(msg);
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
if (!pending.containsKey(u)) return;
        // (cancel removed in non-event context)
        String msg = e.getMessage().trim();
        int slot;
        try { slot = Integer.parseInt(msg); } catch (Exception ex) { e.getPlayer().sendMessage(color("&c숫자(1~6)를 입력하세요.")); return; }
        if (slot<1 || slot>6) { e.getPlayer().sendMessage(color("&c슬롯은 1~6 범위여야 합니다.")); return; }
        PendingAction pa = pending.remove(u);
        p =  e.getPlayer();
final Player fp = p;
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
        // v1~v6 변경권: Ditto(메타몽) 슬롯 보호
        if (type==TicketType.V1 || type==TicketType.V2 || type==TicketType.V3 || type==TicketType.V4 || type==TicketType.V5 || type==TicketType.V6) {
            if (isDittoSlot(p, slot)) {
                p.sendMessage(color("&c[변경권] 메타몽(Ditto)에는 V1~V6 변경권을 사용할 수 없습니다."));
                return;
            }
            // consume on success path later
        }

        switch (type){
            case SHINY:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" shiny",
                        "pokeedit "+p.getName()+" "+slot+" shiny:true",
                        "pokeedit "+p.getName()+" "+slot+" s:1"
                );
                consumeOne(p);
                p.sendMessage(color("&e[이로치권] &f슬롯 "+slot+" 포켓몬을 이로치로 변경 시도."));
                break;
            case NATURE_CHANGE:
                {
                    String[] natures = new String[]{"adamant","bashful","bold","brave","calm","careful","docile","gentle","hardy","hasty","impish","jolly","lax","lonely","mild","modest","naive","quiet","quirky","rash","relaxed","sassy","serious","timid"};
                    String nat = natures[new java.util.Random().nextInt(natures.length)];
                    runConsole("minecraft:pokeedit " + p.getName() + " " + slot + " nature:" + nat);
                    consumeOne(p);
                    p.sendMessage(color("&d[성격변경권] &f슬롯 " + slot + " 성격을 &d" + nat + " &f로 변경 시도."));
                    break;
                }

            case BIGGEST:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gr:Ginormous",
                        "pokeedit "+p.getName()+" "+slot+" growth:Ginormous",
                        "pokeedit "+p.getName()+" "+slot+" growth:ginormous"
                );
                consumeOne(p);
                p.sendMessage(color("&a[가장큼권] &f슬롯 "+slot+" 크기를 Ginormous로 변경 시도."));
                break;
            case SMALLEST:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gr:Microscopic",
                        "pokeedit "+p.getName()+" "+slot+" growth:Microscopic",
                        "pokeedit "+p.getName()+" "+slot+" growth:microscopic"
                );
                consumeOne(p);
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
                java.util.Random r = new java.util.Random();
                int hp=r.nextInt(32), atk=r.nextInt(32), def=r.nextInt(32), spa=r.nextInt(32), spd=r.nextInt(32), spe=r.nextInt(32);
                tryCommands(
                        String.format("pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspd:%d", p.getName(), slot, hp, atk, def, spa, spd, spe),
                        String.format("pokeedit %s %d ivhp:%d ivatk:%d ivdef:%d ivspatk:%d ivspdef:%d ivspeed:%d", p.getName(), slot, hp, atk, def, spa, spd, spe)
                );
                consumeOne(p);
                p.sendMessage(color("&6[랜덤개체값권] &f슬롯 "+slot+" IV 전부 랜덤화 시도."));
                break;
            case GENDER_MALE:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gender:male",
                        "pokeedit "+p.getName()+" "+slot+" g:male",
                        "pokeedit "+p.getName()+" "+slot+" sex:male"
                );
                consumeOne(p);
                p.sendMessage(color("&b[성별변경권] &f슬롯 "+slot+" 수컷으로 변경 시도."));
                break;
            case GENDER_FEMALE:
                tryCommands(
                        "pokeedit "+p.getName()+" "+slot+" gender:female",
                        "pokeedit "+p.getName()+" "+slot+" g:female",
                        "pokeedit "+p.getName()+" "+slot+" sex:female"
                );
                consumeOne(p);
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

    // N개=31, 나머지=랜덤(0~31)
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

        consumeOne(p);
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
            consumeOne(p);
            p.sendMessage(color("&b[전설선택권] &f지급 완료: &b")+localize(eng));
            p.closeInventory();
        }
    }


    private boolean isDittoSlot(Player p, int slot) {
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
            
            meta.getPersistentDataContainer().set(HEART_VER, org.bukkit.persistence.PersistentDataType.INTEGER, HEART_VERSION);item.setItemMeta(meta);
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
    private void markAsTicket(org.bukkit.inventory.ItemStack item, TicketType type){
        if (item == null) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (KEY_TAG != null) pdc.set(KEY_TAG, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
        if (KEY_TYPE != null) pdc.set(KEY_TYPE, org.bukkit.persistence.PersistentDataType.STRING, type != null ? type.id : "UNKNOWN");
        // ensure visible name at least
        if (type != null) {
            String name = type.displayName != null ? type.displayName : type.name();
            try { meta.setDisplayName(name); } catch (Throwable ignored) {}
        }
        item.setItemMeta(meta);
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
                if (id != null) return TicketType.fromKorean(id);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private org.bukkit.inventory.ItemStack createTicket(TicketType type, int amount){
        // Heart scale는 별도 명령으로 처리하고, 여기서는 일반 티켓 기본 생성
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER, Math.max(1, amount));
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        String name = type != null && type.displayName != null ? type.displayName : "권";
        try { meta.setDisplayName(name); } catch (Throwable ignored) {}
        java.util.List<String> lore = new java.util.ArrayList<>();
        if (type != null && type.displayName != null) lore.add(type.displayName);
        lore.add(org.bukkit.ChatColor.GRAY + "우클릭해서 사용");
        try { meta.setLore(lore); } catch (Throwable ignored) {}
        it.setItemMeta(meta);
        markAsTicket(it, type);
        return it;
    }

    private void consumeOne(org.bukkit.entity.Player p){
        try {
            org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
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

}
