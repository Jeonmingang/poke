package com.minkang.ultimate.pocketphoto.util;

import com.minkang.ultimate.pocketphoto.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;

public class PhotoItemUtil {
    public static boolean givePhotoItem(Player p, PixelmonSpecUtil.Result res) {
        ItemStack it = buildPhotoItem(p, res);
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(it);
        return leftover == null || leftover.isEmpty();
    }

    public static ItemStack buildPhotoItem(Player p, PixelmonSpecUtil.Result r) {
        // 스프라이트 지도(기본) → 실패 시 종이
        boolean useMap = Main.get().getConfig().getBoolean("use-map-sprite", true);
        ItemStack item;
        if (useMap) {
            ItemStack map = tryBuildMapWithSprite(r.species, r.form);
            item = (map != null ? map : new ItemStack(Material.PAPER));
        } else item = new ItemStack(Material.PAPER);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String title = Main.get().getConfig()
                .getString("photo-title-format","§e[ 포켓 사진 ] §f%species% §7Lv %level%")
                .replace("%species%", r.species)
                .replace("%level%", String.valueOf(r.level));
        meta.setDisplayName(title);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GREEN + "닉네임 §f: " + (nz(r.nickname) ? r.nickname : "없음"));
        lore.add(ChatColor.DARK_AQUA + "알깨진정도 §f: " + r.hatchProgress);
        lore.add(ChatColor.YELLOW + "이로치 §f: " + (r.shiny ? "있음" : "없음"));
        lore.add(ChatColor.GRAY + "폼 §f: " + (nz(r.form) ? r.form : "없음"));
        lore.add(ChatColor.RED + "체력 §f: " + r.hp);
        lore.add(ChatColor.LIGHT_PURPLE + "성별 §f: " + r.gender);
        lore.add(ChatColor.GOLD + "포켓몬볼 §f: " + (nz(r.ball) ? r.ball : "없음"));
        lore.add(ChatColor.BLUE + "크기 §f: " + r.growth);
        lore.add(ChatColor.GREEN + "성격 §f: " + r.nature);
        lore.add(ChatColor.GOLD + "특성 §f: " + r.ability);
        lore.add(ChatColor.AQUA + "기술 §f: " + joinMovesWithPP(r));     // 예: 놀래키기[15/15], 그림자분신[15/15]
        lore.add(ChatColor.DARK_GREEN + "능력치 §f: " + r.statsLine);     // 6개 능력치 라인
        lore.add(ChatColor.AQUA + "개체값 §f: " + r.ivs);
        lore.add(ChatColor.BLUE + "노력치 §f: " + r.evs);
        lore.add(ChatColor.GRAY + "친밀도 §f: " + r.friendship);
        lore.add(ChatColor.DARK_PURPLE + "중성화 §f: " + (r.neutered ? "예" : "아니오"));

        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(Main.get(), "pp_data");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.STRING, JsonUtil.encode(r.toJsonForItem()));

        item.setItemMeta(meta);
        return item;
    }

    private static boolean nz(String s){ return s != null && !s.trim().isEmpty(); }

    private static String joinMovesWithPP(PixelmonSpecUtil.Result r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.moves.size(); i++) {
            if (i > 0) sb.append(", ");
            String name = r.moves.get(i);
            String pp = "";
            if (i < r.movePP.size() && i < r.moveMaxPP.size()) {
                pp = "[" + r.movePP.get(i) + "/" + r.moveMaxPP.get(i) + "]";
            }
            sb.append(name).append(pp);
        }
        return sb.toString();
    }

    /** 픽셀몬 스프라이트 PNG를 찾아 지도에 그리기 */
    private static ItemStack tryBuildMapWithSprite(String species, String form) {
        try {
            BufferedImage img = tryLoadSprite(species, form);
            if (img == null) return null;
            ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w == null) return null;
            MapView mv = Bukkit.createMap(w);
            for (MapRenderer r : mv.getRenderers()) mv.removeRenderer(r);
            mv.setScale(MapView.Scale.NORMAL);
            mv.addRenderer(new MapRenderer() {
                private boolean drawn = false;
                @Override public void render(MapView map, MapCanvas canvas, Player player) {
                    if (drawn) return; drawn = true;
                    java.awt.Image scaled = img.getScaledInstance(128, 128, java.awt.Image.SCALE_DEFAULT);
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.drawImage(scaled, 0, 0, null); g.dispose();
                    canvas.drawImage(0, 0, MapPalette.resizeImage(out));
                }
            });
            MapMeta meta = (MapMeta) map.getItemMeta();
            meta.setMapView(mv);
            map.setItemMeta(meta);
            return map;
        } catch (Throwable t) { return null; }
    }

    private static BufferedImage tryLoadSprite(String species, String form) {
        try {
            String base = species.toLowerCase().replace(' ', '_').replace('-', '_').replace('.', '_');
            String f = (form == null ? "" : form.trim().toLowerCase().replace(' ', '_').replace('-', '_'));
            String withForm = (f.isEmpty() || "ordinary".equals(f) || "none".equals(f) || "null".equals(f) || "0".equals(f))
                    ? base : base + "_" + f;

            String[] keys = new String[]{withForm, base};
            String[] roots = new String[]{
                "assets/pixelmon/textures/gui/sprites/pokemon/",
                "assets/pixelmon/textures/pokemon/",
                "assets/pixelmon/textures/gui/pokemon/",
                "assets/pixelmon/textures/sprites/pokemon/"
            };
            ClassLoader cl = Class.forName("com.pixelmonmod.pixelmon.Pixelmon").getClassLoader();
            for (String k : keys) for (String r : roots) {
                String path = r + k + ".png";
                try (InputStream is = cl.getResourceAsStream(path)) { if (is != null) return ImageIO.read(is); } catch (Throwable ignore){}
            }
            return null;
        } catch (Throwable t) { return null; }
    }
}
