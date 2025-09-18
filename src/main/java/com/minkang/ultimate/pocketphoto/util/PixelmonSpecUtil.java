package com.minkang.ultimate.pocketphoto.util;

import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;

public class PixelmonSpecUtil {
    public static class Result {
        public boolean success; public String message;

        public String species="Unknown", nickname="", gender="무성", nature="불명", ability="불명", growth="불명", form="", ball="";
        public int level=1, friendship=0, hp=0, hatchProgress=-1, slot=1; public boolean shiny=false, neutered=false;

        public String ivs="?", evs="0 0 0 0 0 0", statsLine="";
        public List<String> moves=new ArrayList<>();
        public List<Integer> movePP=new ArrayList<>(), moveMaxPP=new ArrayList<>();

        public String specString="", nbtBase64="";
        public Map<String,Object> toJsonForItem(){
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("slot",slot); m.put("species",species); m.put("spec",specString); m.put("level",level); m.put("nickname",nickname);
            m.put("gender",gender); m.put("nature",nature); m.put("ability",ability); m.put("ivs",ivs); m.put("evs",evs); m.put("shiny",shiny);
            m.put("moves",moves); m.put("friendship",friendship); m.put("ball",ball); m.put("growth",growth); m.put("form",form);
            m.put("hp",hp); m.put("hatch",hatchProgress); m.put("neutered",neutered); m.put("stats",statsLine);
            m.put("pp",movePP); m.put("maxpp",moveMaxPP); m.put("nbt",nbtBase64); return m;
        }
    }

    /* 파티에서 추출 + NBT 저장 + 슬롯 비우기 */
    public static Result extractAndRemovePartyPokemonWithNBT(Player p, int slot1to6) {
        Result r = new Result(); r.slot=slot1to6;
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Object party = storageProxy.getMethod("getParty", java.util.UUID.class).invoke(null, p.getUniqueId());
            if (party == null) return fail(r,"파티를 가져오지 못했습니다.");
            int idx = slot1to6-1;
            Object pokemon = party.getClass().getMethod("get", int.class).invoke(party, idx);
            if (pokemon == null) return fail(r,"해당 슬롯에 포켓몬이 없습니다.");

            fillInfoFromPokemon(pokemon, r);
            r.specString = buildSpecWithoutMoves(r); // 폴백 스펙(기술 제외)
            r.nbtBase64 = tryWriteNbtBase64(pokemon);

            try {
                party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"))
                     .invoke(party, idx, null);
            } catch (Throwable ignore){}

            r.success=true; r.message="OK"; return r;
        } catch (Throwable t) { return fail(r,"Pixelmon API 접근 실패: "+t.getClass().getSimpleName()); }
    }

    /* 사진 우클릭: NBT로 복원 시도 */
    public static boolean restoreFromNBT(Player p, String base64, int slot) {
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64);
            Class<?> nbt = Class.forName("net.minecraft.nbt.CompoundNBT");
            Object tag;
            try {
                Class<?> cst = Class.forName("net.minecraft.nbt.CompressedStreamTools");
                tag = cst.getMethod("readCompressed", java.io.InputStream.class)
                         .invoke(null, new java.io.ByteArrayInputStream(data));
            } catch(Throwable t){ tag = nbt.getConstructor().newInstance(); }
            Object poke=null;
            try {
                Class<?> factory = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory");
                poke = factory.getMethod("create", nbt).invoke(null, tag);
            } catch(Throwable t){
                Class<?> pokemonCls = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
                try { poke = pokemonCls.getMethod("readFromNBT", nbt).invoke(null, tag); } catch(Throwable ignore){}
            }
            if (poke==null) return false;

            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Object party = storageProxy.getMethod("getParty", java.util.UUID.class).invoke(null, p.getUniqueId());
            party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"))
                 .invoke(party, slot-1, poke);
            return true;
        } catch (Throwable t) { return false; }
    }

    /* 핵심: 사람이 읽는 이름으로 모두 정제 */
    private static void fillInfoFromPokemon(Object pk, Result r) throws Exception {
        // 종 이름
        try {
            Object sp = pk.getClass().getMethod("getSpecies").invoke(pk);
            String n = asText(sp, "getLocalizedName", "getName");
            r.species = stripPkg(n!=null?n:String.valueOf(sp));
        } catch (Throwable ignore){}

        // 닉네임/레벨/성별
        try { Object o = pk.getClass().getMethod("getNickname").invoke(pk); if(o!=null) r.nickname=String.valueOf(o);} catch(Throwable ignore){}
        try { Object o = pk.getClass().getMethod("getLevel").invoke(pk); if(o instanceof Number) r.level=((Number)o).intValue();} catch(Throwable ignore){}
        try {
            String g = String.valueOf(pk.getClass().getMethod("getGender").invoke(pk));
            r.gender = "MALE".equalsIgnoreCase(g)?"수컷": "FEMALE".equalsIgnoreCase(g)?"암컷": "무성";
        } catch(Throwable ignore){}

        // 성격/특성
        try { Object o = pk.getClass().getMethod("getNature").invoke(pk);
              if(o!=null) r.nature = stripPkg(asText(o,"getName","getLocalizedName")); } catch(Throwable ignore){}
        try { Object o = pk.getClass().getMethod("getAbility").invoke(pk);
              String a = asText(o,"getLocalizedName","getName","getAbilityName");
              r.ability = stripAfter(stripPkg(nz(a)?a:String.valueOf(o)),'@'); } catch(Throwable ignore){}

        // 기타
        try { r.shiny = (Boolean) pk.getClass().getMethod("isShiny").invoke(pk); } catch(Throwable ignore){}
        try { Object o = pk.getClass().getMethod("getFriendship").invoke(pk); if(o instanceof Number) r.friendship=((Number)o).intValue(); } catch(Throwable ignore){}
        try { Object o = pk.getClass().getMethod("getGrowth").invoke(pk); if(o!=null) r.growth = stripPkg(String.valueOf(o)); } catch(Throwable ignore){}
        try {
            Object fo = pk.getClass().getMethod("getForm").invoke(pk);
            String f = asText(fo,"getLocalizedName","getName","getFormName");
            if (f==null) f=stripPkg(String.valueOf(fo));
            f = stripAfter(f,'@');
            if (eqAny(f,"ordinary","none","null","0")) f="";
            r.form = f;
        } catch(Throwable ignore){}
        try { Object o = pk.getClass().getMethod("getCaughtBall").invoke(pk); if(o!=null) r.ball = stripPkg(String.valueOf(o)); } catch(Throwable ignore){}
        try { Object o = pk.getClass().getMethod("getEggSteps").invoke(pk); if(o instanceof Number) r.hatchProgress=((Number)o).intValue(); } catch(Throwable ignore){}
        for (String m : new String[]{"isNeutered","getNeutered","isSterilized","getSterilized","isInfertile","isUnbreedable"})
            try { Object v = pk.getClass().getMethod(m).invoke(pk); if(v instanceof Boolean){ r.neutered=(Boolean)v; break; } } catch(Throwable ignore){}

        // 수치계
        r.statsLine = readStatsLine(pk);
        r.hp = readInt(pk, "getHP","getHealth");
        r.ivs = readStats(pk,true);
        r.evs = readStats(pk,false);

        // 기술 4개 + PP
        readMoves(pk, r);
    }

    private static void readMoves(Object pk, Result r){
        try {
            Object moveset;
            try { moveset = pk.getClass().getMethod("getMoveset").invoke(pk); }
            catch (Throwable t) { moveset = pk.getClass().getMethod("getMoves").invoke(pk); }
            if (moveset == null) return;

            try { // size()/get(i)
                int size = ((Number)moveset.getClass().getMethod("size").invoke(moveset)).intValue();
                Method get = moveset.getClass().getMethod("get", int.class);
                for (int i=0;i<size;i++) handleMoveSlot(get.invoke(moveset,i), r);
                return;
            } catch (Throwable ignore){}

            if (moveset instanceof Iterable)
                for (Object slot : (Iterable<?>) moveset) handleMoveSlot(slot, r);
        } catch (Throwable ignore){}
    }
    private static void handleMoveSlot(Object slot, Result r){
        if (slot==null) return;
        String name=null; Object atk=null;
        for (String m : new String[]{"getAttack","getMove","getActualMove","getAttackBase","getBaseMove","getBase"})
            try { atk = slot.getClass().getMethod(m).invoke(slot); if(atk!=null) break; } catch(Throwable ignore){}
        if (atk!=null) name = asText(atk,"getDisplayName","getAttackName","getName","getLocalizedName");
        if (name==null){ String raw=String.valueOf(slot); int b=raw.indexOf('['); name=(b>0? raw.substring(0,b):raw); }
        name = stripPkg(name);
        if (nz(name)) r.moves.add(name.trim());

        Integer cur = readIntObj(slot,"getPp","getPP","getCurrentPp","getCurrentPP","pp");
        Integer mx  = readIntObj(slot,"getMaxPp","getMaxPP","getMaxpp","maxpp","maxPP");
        r.movePP.add(cur!=null?cur:0);
        r.moveMaxPP.add(mx!=null?mx:(cur!=null?cur:0));
    }

    /* ===== 공용 헬퍼 ===== */
    private static Result fail(Result r,String msg){ r.success=false; r.message=msg; return r; }
    private static boolean nz(String s){ return s!=null && !s.trim().isEmpty(); }
    private static boolean eqAny(String s, String...a){ if(s==null) return false; for(String x:a) if(s.equalsIgnoreCase(x)) return true; return false; }

    /** ITextComponent 지원: getString() 우선 → 일반 toString() */
    private static String asText(Object o, String... accessors){
        if (o == null) return null;
        for (String m : accessors){
            try {
                Object v = o.getClass().getMethod(m).invoke(o);
                if (v != null) return fromComponentOrString(v);
            } catch (Throwable ignore){}
        }
        return fromComponentOrString(o);
    }
    private static String fromComponentOrString(Object v){
        try { // net.minecraft.util.text.ITextComponent (1.16.5)
            Class<?> ic = Class.forName("net.minecraft.util.text.ITextComponent");
            if (ic.isInstance(v)) {
                Object s = ic.getMethod("getString").invoke(v);
                if (s != null) return String.valueOf(s);
            }
        } catch (Throwable ignore){}
        return String.valueOf(v);
    }

    private static String stripAfter(String s,char c){ int i=s.indexOf(c); return (i>=0? s.substring(0,i):s); }
    private static String stripPkg(String s){ if(s==null) return null; int i=s.lastIndexOf('.'); return (i>=0? s.substring(i+1):s); }

    private static int readInt(Object o,String...m){ for(String mm:m) try{ Object v=o.getClass().getMethod(mm).invoke(o); if(v instanceof Number) return ((Number)v).intValue(); }catch(Throwable ignore){} return 0; }
    private static Integer readIntObj(Object o,String...m){ for(String mm:m) try{ Object v=o.getClass().getMethod(mm).invoke(o); if(v instanceof Number) return ((Number)v).intValue(); }catch(Throwable ignore){} return null; }

    private static String readStats(Object pk, boolean iv){
        try {
            Object stats = pk.getClass().getMethod(iv? "getIVs" : "getEVs").invoke(pk);
            int[] arr = new int[6];
            try { // getArray()
                Object a = stats.getClass().getMethod("getArray").invoke(stats);
                if (a!=null && a.getClass().isArray()){
                    for(int i=0;i<Math.min(java.lang.reflect.Array.getLength(a),6);i++){
                        Object v=java.lang.reflect.Array.get(a,i); if(v instanceof Number) arr[i]=((Number)v).intValue();
                    }
                    return join6(arr);
                }
            } catch (Throwable ignore){}
            try { // get(int)
                Method get = stats.getClass().getMethod("get", int.class);
                for(int i=0;i<6;i++){ Object v = get.invoke(stats,i); if(v instanceof Number) arr[i]=((Number)v).intValue(); }
                return join6(arr);
            } catch (Throwable ignore){}
        } catch (Throwable ignore){}
        return iv? "31 31 31 31 31 31" : "0 0 0 0 0 0";
    }
    private static String readStatsLine(Object pk){
        try {
            Object st = pk.getClass().getMethod("getStats").invoke(pk);
            int[] a = new int[6];
            String[] n = {"getHp","getAttack","getDefense","getSpecialAttack","getSpecialDefense","getSpeed"};
            for(int i=0;i<n.length;i++)
                try{ Object v = st.getClass().getMethod(n[i]).invoke(st); if(v instanceof Number) a[i]=((Number)v).intValue(); }catch(Throwable ignore){}
            return join6(a);
        } catch(Throwable t){ return ""; }
    }
    private static String join6(int[] a){ StringBuilder sb=new StringBuilder(); for(int i=0;i<6;i++){ if(i>0) sb.append(' '); sb.append(a[i]); } return sb.toString(); }

    /* pokegive 폴백용: moves 제외(파서 충돌 방지) */
    public static String buildSpecWithoutMoves(Result r){
        List<String> parts=new ArrayList<>();
        parts.add(token(r.species)); parts.add("level:"+r.level);
        if(nz(r.gender)) parts.add("gender:"+token(r.gender));
        if(nz(r.nature)) parts.add("nature:"+token(r.nature));
        if(nz(r.ability)) parts.add("ability:"+token(r.ability));
        if(r.shiny) parts.add("shiny");
        if(nz(r.form)) parts.add("form:\""+r.form.replace("\"","'")+"\"");
        if(nz(r.ball)) parts.add("ball:\""+r.ball.replace("\"","'")+"\"");
        return String.join(" ", parts);
    }
    private static String token(String s){ String t=s.trim(); return (t.contains(" ")||t.contains(":"))? "\""+t.replace("\"","'")+"\"" : t; }

    private static String tryWriteNbtBase64(Object pk){
        try {
            Class<?> nbt = Class.forName("net.minecraft.nbt.CompoundNBT");
            Object tag = nbt.getConstructor().newInstance();
            boolean wrote=false;
            for(String m:new String[]{"writeToNBT","writeNBT","save","write"})
                try{ pk.getClass().getMethod(m,nbt).invoke(pk,tag); wrote=true; break; } catch(Throwable ignore){}
            if(!wrote) try{
                Class<?> ser = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonSerializer");
                ser.getMethod("write", pk.getClass(), nbt).invoke(null, pk, tag); wrote=true;
            } catch(Throwable ignore){}
            if(!wrote) return "";
            try {
                Class<?> cst=Class.forName("net.minecraft.nbt.CompressedStreamTools");
                java.io.ByteArrayOutputStream baos=new java.io.ByteArrayOutputStream();
                cst.getMethod("writeCompressed", nbt, java.io.OutputStream.class).invoke(null, tag, baos);
                return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch(Throwable t){ return ""; }
        } catch(Throwable t){ return ""; }
    }
}
