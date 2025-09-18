package com.minkang.ultimate.pocketphoto.util;

import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;

public class PixelmonSpecUtil {
    public static class Result {
        public boolean success;
        public String message;
        public String species = "Unknown";
        public String nickname = "";
        public int level = 1;
        public String gender = "무성";
        public String nature = "불명";
        public String ability = "불명";
        public String ivs = "?";
        public String evs = "0 0 0 0 0 0";
        public String statsLine = "";
        public String growth = "불명";
        public boolean shiny = false;
        public String form = "";
        public int friendship = 0;
        public String ball = "";
        public int hp = 0;
        public int hatchProgress = -1;
        public boolean neutered = false;
        public List<String> moves = new ArrayList<>();
        public List<Integer> movePP = new ArrayList<>();
        public List<Integer> moveMaxPP = new ArrayList<>();
        public int slot = 1;
        public String specString = "";
        public String nbtBase64 = "";
        public Map<String, Object> toJsonForItem() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", slot);
            m.put("spec", specString);
            m.put("species", species);
            m.put("level", level);
            m.put("nickname", nickname);
            m.put("gender", gender);
            m.put("nature", nature);
            m.put("ability", ability);
            m.put("ivs", ivs);
            m.put("evs", evs);
            m.put("shiny", shiny);
            m.put("moves", moves);
            m.put("friendship", friendship);
            m.put("ball", ball);
            m.put("growth", growth);
            m.put("form", form);
            m.put("hp", hp);
            m.put("hatch", hatchProgress);
            m.put("neutered", neutered);
            m.put("stats", statsLine);
            m.put("pp", movePP);
            m.put("maxpp", moveMaxPP);
            m.put("nbt", nbtBase64);
            return m;
        }
    }

    /* --------- Party 읽기/제거 --------- */
    public static Result extractAndRemovePartyPokemonWithNBT(Player p, int slot1to6) {
        Result r = new Result();
        r.slot = slot1to6;
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) { r.success=false; r.message="파티를 가져오지 못했습니다."; return r; }
            int index = slot1to6 - 1;
            Method get = party.getClass().getMethod("get", int.class);
            Object pokemon = get.invoke(party, index);
            if (pokemon == null) { r.success=false; r.message="해당 슬롯에 포켓몬이 없습니다."; return r; }
            fillInfoFromPokemon(pokemon, r);
            r.specString = buildSpecStringFromPokemon(pokemon, r);
            r.nbtBase64 = tryWriteNbtBase64(pokemon);
            try {
                Method set = party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"));
                set.invoke(party, new Object[]{index, null});
            } catch (Throwable ignored) {}
            r.success = true; r.message="OK"; return r;
        } catch (Throwable t) {
            r.success=false; r.message="Pixelmon API 접근 실패: " + t.getClass().getSimpleName(); return r;
        }
    }

    /* --------- NBT 복원 --------- */
    public static boolean restoreFromNBT(Player p, String base64, int slot) {
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64);
            Class<?> nbtCls = Class.forName("net.minecraft.nbt.CompoundNBT");
            Object tag;
            try {
                Class<?> cst = Class.forName("net.minecraft.nbt.CompressedStreamTools");
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                Method read = cst.getMethod("readCompressed", java.io.InputStream.class);
                tag = read.invoke(null, bais);
            } catch (Throwable t) {
                tag = nbtCls.getConstructor().newInstance();
            }
            Object poke = null;
            try {
                Class<?> factory = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory");
                Method m = factory.getMethod("create", nbtCls);
                poke = m.invoke(null, tag);
            } catch (Throwable t1) {
                try {
                    Class<?> pokemonCls = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
                    Method m = pokemonCls.getMethod("readFromNBT", nbtCls);
                    poke = m.invoke(null, tag);
                } catch (Throwable ignored) {}
            }
            if (poke == null) return false;
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;
            Method set = party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"));
            set.invoke(party, slot - 1, poke);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /* --------- 개체 → 정보 채우기 --------- */
    private static void fillInfoFromPokemon(Object pokemon, Result r) throws Exception {
        // species
        try {
            Object o = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
            String name = stringify(o, "getName", "getLocalizedName");
            if (name == null) name = String.valueOf(o);
            r.species = stripPkg(name);
        } catch (Throwable ignored) {}
        // nickname
        try { Object o = pokemon.getClass().getMethod("getNickname").invoke(pokemon); if (o != null) r.nickname = String.valueOf(o); } catch (Throwable ignored) {}
        // level
        try { Object o = pokemon.getClass().getMethod("getLevel").invoke(pokemon); if (o instanceof Number) r.level = ((Number)o).intValue(); } catch (Throwable ignored) {}
        // gender
        try { Object o = pokemon.getClass().getMethod("getGender").invoke(pokemon); if (o != null) {
                String g = String.valueOf(o);
                if ("NONE".equalsIgnoreCase(g)) g = "무성";
                if ("MALE".equalsIgnoreCase(g)) g = "수컷";
                if ("FEMALE".equalsIgnoreCase(g)) g = "암컷";
                r.gender = g;
            }} catch (Throwable ignored) {}
        // nature
        try { Object o = pokemon.getClass().getMethod("getNature").invoke(pokemon); if (o != null) r.nature = stripPkg(stringify(o, "getName", "getLocalizedName")); } catch (Throwable ignored) {}
        // ability
        try {
            Object ab = pokemon.getClass().getMethod("getAbility").invoke(pokemon);
            String an = stringify(ab, "getLocalizedName", "getName", "getAbilityName");
            if (an == null) an = stripPkg(String.valueOf(ab));
            r.ability = stripAfter(stripPkg(an), '@');
        } catch (Throwable ignored) {}
        // shiny
        try { Object o = pokemon.getClass().getMethod("isShiny").invoke(pokemon); if (o instanceof Boolean) r.shiny = (Boolean) o; } catch (Throwable ignored) {}
        // friendship
        try { Object o = pokemon.getClass().getMethod("getFriendship").invoke(pokemon); if (o instanceof Number) r.friendship = ((Number) o).intValue(); } catch (Throwable ignored) {}
        // growth
        try { Object o = pokemon.getClass().getMethod("getGrowth").invoke(pokemon); if (o != null) r.growth = stripPkg(String.valueOf(o)); } catch (Throwable ignored) {}
        // form (Ordinary/None/null 은 숨김)
        try {
            Object fo = pokemon.getClass().getMethod("getForm").invoke(pokemon);
            String fn = stringify(fo, "getName", "getFormName", "getLocalizedName");
            if (fn == null) fn = stripPkg(String.valueOf(fo));
            fn = stripAfter(fn, '@');
            if (fn.equalsIgnoreCase("ordinary") || fn.equalsIgnoreCase("none") ||
                fn.equalsIgnoreCase("null") || fn.equals("0")) fn = "";
            r.form = fn;
        } catch (Throwable ignored) {}
        // ball
        try { Object o = pokemon.getClass().getMethod("getCaughtBall").invoke(pokemon); if (o != null) r.ball = stripPkg(String.valueOf(o)); } catch (Throwable ignored) {}
        // egg steps / hatch
        try { Object o = pokemon.getClass().getMethod("getEggSteps").invoke(pokemon); if (o instanceof Number) r.hatchProgress = ((Number) o).intValue(); } catch (Throwable ignored) {}
        // neutered
        String[] neuters = new String[]{"isNeutered","getNeutered","isSterilized","getSterilized","isInfertile","isUnbreedable"};
        for (String m : neuters) { try { Object v = pokemon.getClass().getMethod(m).invoke(pokemon); if (v instanceof Boolean) { r.neutered = (Boolean) v; break; } } catch (Throwable ignored) {} }
        // base stats
        r.statsLine = readStatsLine(pokemon);
        r.hp = readStat(pokemon, "getHP", "getHealth");
        r.ivs = readStats(pokemon, true);
        r.evs = readStats(pokemon, false);
        // moves
        readMovesRobust(pokemon, r);
    }

    /* --------- moves 이름/PP 강인식 --------- */
    private static void readMovesRobust(Object pokemon, Result r){
        try {
            Object moveset;
            try { moveset = pokemon.getClass().getMethod("getMoveset").invoke(pokemon); }
            catch (Throwable t) { moveset = pokemon.getClass().getMethod("getMoves").invoke(pokemon); }
            if (moveset == null) return;

            try { // size()/get(i)
                Method sizeM = moveset.getClass().getMethod("size");
                int size = ((Number)sizeM.invoke(moveset)).intValue();
                Method getM = moveset.getClass().getMethod("get", int.class);
                for (int i=0;i<size;i++) handleMoveSlot(getM.invoke(moveset, i), r);
                return;
            } catch (Throwable ignored){}

            if (moveset instanceof Iterable){
                for (Object slot : (Iterable<?>) moveset) handleMoveSlot(slot, r);
            }
        } catch (Throwable ignored){}
    }
    private static void handleMoveSlot(Object slot, Result r){
        if (slot == null) return;
        String name = null;
        Object atk = null;
        String[] accessors = new String[]{"getAttack","getMove","getActualMove","getAttackBase","getBaseMove","getBase"};
        for (String m : accessors){
            try { atk = slot.getClass().getMethod(m).invoke(slot); if (atk != null) break; } catch (Throwable ignored){}
        }
        if (atk != null){
            name = stringify(atk, "getDisplayName","getAttackName","getName","getLocalizedName");
        }
        if (name == null){
            String raw = String.valueOf(slot);
            int br = raw.indexOf('[');  // Aromatherapy[pp 5 ...] → Aromatherapy
            name = (br>0 ? raw.substring(0, br) : raw);
        }
        name = stripPkg(name);
        if (!name.trim().isEmpty()) r.moves.add(name.trim());

        Integer cur = tryInt(slot, "getPp","getPP","getCurrentPp","getCurrentPP","pp");
        Integer mx  = tryInt(slot, "getMaxPp","getMaxPP","getMaxpp","maxpp","maxPP");
        r.movePP.add(cur != null ? cur : 0);
        r.moveMaxPP.add(mx != null ? mx : (cur != null ? cur : 0));
    }

    /* --------- spec/NBT/통합 유틸 --------- */
    private static String tryWriteNbtBase64(Object pokemon) {
        try {
            Class<?> nbtCls = Class.forName("net.minecraft.nbt.CompoundNBT");
            Object tag = nbtCls.getConstructor().newInstance();
            boolean wrote = false;
            String[] methods = new String[]{"writeToNBT", "writeNBT", "save", "write"};
            for (String mName : methods) {
                try { Method m = pokemon.getClass().getMethod(mName, nbtCls); m.invoke(pokemon, tag); wrote = true; break; }
                catch (Throwable ignored) {}
            }
            if (!wrote) {
                try {
                    Class<?> serializer = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonSerializer");
                    Method m = serializer.getMethod("write", pokemon.getClass(), nbtCls);
                    m.invoke(null, pokemon, tag);
                    wrote = true;
                } catch (Throwable ignored) {}
            }
            if (!wrote) return "";
            try {
                Class<?> cst = Class.forName("net.minecraft.nbt.CompressedStreamTools");
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                Method write = cst.getMethod("writeCompressed", nbtCls, java.io.OutputStream.class);
                write.invoke(null, tag, baos);
                return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Throwable t) { return ""; }
        } catch (Throwable t) { return ""; }
    }

    private static String readStatsLine(Object pokemon) {
        try {
            Method getStats = pokemon.getClass().getMethod("getStats");
            Object stats = getStats.invoke(pokemon);
            int[] arr = new int[6];
            String[] names = new String[]{"getHp","getAttack","getDefense","getSpecialAttack","getSpecialDefense","getSpeed"};
            for (int i = 0; i < names.length; i++) {
                try { Object v = stats.getClass().getMethod(names[i]).invoke(stats);
                    if (v instanceof Number) arr[i] = ((Number) v).intValue();
                } catch (Throwable ignored) {}
            }
            return join6(arr);
        } catch (Throwable t) { return ""; }
    }

    private static int readStat(Object pokemon, String... methods) {
        for (String mName : methods) {
            try { Object o = pokemon.getClass().getMethod(mName).invoke(pokemon);
                if (o instanceof Number) return ((Number) o).intValue();
            } catch (Throwable ignored) {}
        }
        return 0;
    }
    private static String readStats(Object pokemon, boolean iv) {
        try {
            Method getObj = pokemon.getClass().getMethod(iv? "getIVs" : "getEVs");
            Object stats = getObj.invoke(pokemon);
            int[] arr = new int[6];
            try {
                Method m = stats.getClass().getMethod("getArray");
                Object o = m.invoke(stats);
                if (o != null && o.getClass().isArray()) {
                    for (int i = 0; i < Math.min(java.lang.reflect.Array.getLength(o), 6); i++) {
                        Object v = java.lang.reflect.Array.get(o, i);
                        if (v instanceof Number) arr[i] = ((Number) v).intValue();
                    }
                    return join6(arr);
                }
            } catch (Throwable ignored) {}
            try {
                Method m = stats.getClass().getMethod("get", int.class);
                for (int i = 0; i < 6; i++) {
                    Object v = m.invoke(stats, i);
                    if (v instanceof Number) arr[i] = ((Number) v).intValue();
                }
                return join6(arr);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return iv ? "31 31 31 31 31 31" : "0 0 0 0 0 0";
    }

    private static String join6(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) { if (i > 0) sb.append(' '); sb.append(arr[i]); }
        return sb.toString();
    }

    private static String stringify(Object o, String... tries){
        if (o == null) return null;
        for (String m : tries){
            try { Method mm = o.getClass().getMethod(m); Object v = mm.invoke(o); if (v != null) return String.valueOf(v); } catch (Throwable ignored){}
        }
        return String.valueOf(o);
    }
    private static String stripAfter(String s, char ch){ int i = s.indexOf(ch); return (i >= 0 ? s.substring(0, i) : s); }
    private static String stripPkg(String s){ if (s==null) return null; int i=s.lastIndexOf('.'); return (i>=0? s.substring(i+1): s); }
    private static String esc(String s) { return '\"' + s.replace("\"", "'") + '\"'; }
    private static boolean nz(String s) { return s != null && !s.trim().isEmpty() && !"불명".equals(s); }
    private static String cleanToken(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.contains(" ") || t.contains(":")) return "\"" + t.replace("\"","'") + "\"";
        return t;
    }

    private static String buildSpecStringFromPokemon(Object pokemon, Result r) {
        List<String> parts = new ArrayList<>();
        parts.add(cleanToken(r.species));
        parts.add("level:" + r.level);
        if (r.nickname != null && !r.nickname.isEmpty()) parts.add("nickname:" + esc(r.nickname));
        if (nz(r.gender)) parts.add("gender:" + cleanToken(r.gender));
        if (nz(r.nature)) parts.add("nature:" + cleanToken(r.nature));
        if (nz(r.ability)) parts.add("ability:" + cleanToken(r.ability));
        if (r.shiny) parts.add("shiny");
        if (nz(r.form)) parts.add("form:" + "\"" + r.form.replace("\"","'") + "\"");
        if (nz(r.ball)) parts.add("ball:" + "\"" + r.ball.replace("\"","'") + "\"");
        // moves 는 스펙에서 제외 (파서 충돌 방지)
        return String.join(" ", parts);
    }

    public static String buildSafeSpecFromMap(Map<String, Object> m) {
        Result r = new Result();
        Object sp = m.get("species");
        if (sp == null) sp = m.get("spec");
        r.species = String.valueOf(sp != null ? sp : "Unknown");
        Object lvl = m.get("level");
        r.level = (lvl instanceof Number) ? ((Number) lvl).intValue() : 1;
        r.gender = String.valueOf(m.getOrDefault("gender", ""));
        r.nature = String.valueOf(m.getOrDefault("nature", ""));
        r.ability = String.valueOf(m.getOrDefault("ability", ""));
        Object shinyObj = m.get("shiny");
        r.shiny = shinyObj instanceof Boolean ? (Boolean) shinyObj : false;
        r.form = String.valueOf(m.getOrDefault("form", ""));
        r.ball = String.valueOf(m.getOrDefault("ball", ""));
        return buildSpecWithoutMoves(r);
    }

    public static String buildSpecWithoutMoves(Result r) {
        List<String> parts = new ArrayList<>();
        parts.add(cleanToken(r.species));
        parts.add("level:" + r.level);
        if (nz(r.gender)) parts.add("gender:" + cleanToken(r.gender));
        if (nz(r.nature)) parts.add("nature:" + cleanToken(r.nature));
        if (nz(r.ability)) parts.add("ability:" + cleanToken(r.ability));
        if (r.shiny) parts.add("shiny");
        if (nz(r.form)) parts.add("form:" + "\"" + r.form.replace("\"","'") + "\"");
        if (nz(r.ball)) parts.add("ball:" + "\"" + r.ball.replace("\"","'") + "\"");
        return String.join(" ", parts);
    }
}
