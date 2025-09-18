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
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PhotoItemUtil {
    public static boolean givePhotoItem(Player p, PixelmonSpecUtil.Result res) {
        ItemStack it = buildPhotoItem(p, res);
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(it);
        return leftover == null || leftover.isEmpty();
    }

    public static ItemStack buildPhotoItem(Player p, PixelmonSpecUtil.Result res) {
        boolean useMap = Main.get().getConfig().getBoolean("use-map-sprite", true);
        ItemStack item;
        if (useMap) {
            ItemStack mapItem = tryBuildMapWithSprite(res.species, res.form);
            item = (mapItem != null ? mapItem : new ItemStack(Material.PAPER));
        } else {
            item = new ItemStack(Material.PAPER);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String titleFmt = Main.get().getConfig().getString("photo-title-format","§e[ 포켓 사진 ] §f%species% §7Lv %level%");
        String title = titleFmt.replace("%species%", res.species).replace("%level%", String.valueOf(res.level));
        meta.setDisplayName(title);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GREEN + "닉네임 §f: " + (res.nickname == null || res.nickname.isEmpty() ? "없음" : res.nickname));
        lore.add(ChatColor.DARK_AQUA + "알깨진정도 §f: " + res.hatchProgress);
        lore.add(ChatColor.YELLOW + "이로치 §f: " + (res.shiny ? "예" : "없음"));
        if (res.form != null && !res.form.isEmpty()) lore.add(ChatColor.GRAY + "폼 §f: " + res.form);
        lore.add(ChatColor.RED + "체력 §f: " + res.hp);
        lore.add(ChatColor.LIGHT_PURPLE + "성별 §f: " + res.gender);
        if (res.ball != null && !res.ball.isEmpty()) lore.add(ChatColor.GOLD + "볼 §f: " + res.ball);
        lore.add(ChatColor.BLUE + "크기 §f: " + res.growth);
        lore.add(ChatColor.GREEN + "성격 §f: " + res.nature);
        lore.add(ChatColor.GOLD + "특성 §f: " + res.ability);
        lore.add(ChatColor.AQUA + "기술 §f: " + joinMovesWithPP(res));
        lore.add(ChatColor.DARK_GREEN + "능력치 §f: " + res.statsLine);
        lore.add(ChatColor.AQUA + "개체값 §f: " + res.ivs);
        lore.add(ChatColor.BLUE + "노력치 §f: " + res.evs);
        lore.add(ChatColor.GRAY + "친밀도 §f: " + res.friendship);
        lore.add(ChatColor.DARK_PURPLE + "중성화 §f: " + (res.neutered ? "예" : "아니오"));

        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(Main.get(), "pp_data");
        Map<String, Object> json = res.toJsonForItem();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.STRING, JsonUtil.encode(json));

        item.setItemMeta(meta);
        return item;
    }

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

    /** 픽셀몬 스프라이트 PNG를 Pixelmon 리소스에서 찾아 지도(Map)에 그려 넣는다 */
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
                @Override
                public void render(MapView map, MapCanvas canvas, Player player) {
                    if (drawn) return;
                    drawn = true;
                    java.awt.Image scaled = img.getScaledInstance(128, 128, java.awt.Image.SCALE_DEFAULT);
                    BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = out.createGraphics();
                    g.drawImage(scaled, 0, 0, null);
                    g.dispose();
                    canvas.drawImage(0, 0, MapPalette.resizeImage(out));
                }
            });
            MapMeta meta = (MapMeta) map.getItemMeta();
            meta.setMapView(mv);
            map.setItemMeta(meta);
            return map;
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage tryLoadSprite(String species, String form) {
        try {
            String base = species.toLowerCase().replace(' ', '_').replace('-', '_').replace('.', '_');
            String f = (form == null ? "" : form.trim().toLowerCase().replace(' ', '_').replace('-', '_'));
            String withForm = (f.isEmpty() || "ordinary".equals(f) || "none".equals(f) || "null".equals(f) || "0".equals(f))
                    ? base : base + "_" + f;

            // Pixelmon 1.16 계열에서 쓰일 수 있는 경로 후보들
            String[] keys = new String[]{
                withForm, base
            };
            String[] roots = new String[]{
                "assets/pixelmon/textures/gui/sprites/pokemon/",
                "assets/pixelmon/textures/pokemon/",
                "assets/pixelmon/textures/gui/pokemon/",
                "assets/pixelmon/textures/sprites/pokemon/"
            };
            ClassLoader cl = Class.forName("com.pixelmonmod.pixelmon.Pixelmon").getClassLoader();
            for (String k : keys) {
                for (String r : roots) {
                    String path = r + k + ".png";
                    try (InputStream is = cl.getResourceAsStream(path)) {
                        if (is != null) return ImageIO.read(is);
                    } catch (Throwable ignored) {}
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
