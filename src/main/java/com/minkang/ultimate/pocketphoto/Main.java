package com.minkang.ultimate.pocketphoto;

import com.minkang.ultimate.pocketphoto.cmd.PocketPhotoCommand;
import com.minkang.ultimate.pocketphoto.listener.PhotoItemListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    private static Main inst;
    private boolean pixelmonPresent;

    @Override
    public void onEnable() {
        inst = this;
        saveDefaultConfig();
        pixelmonPresent = classExists("com.pixelmonmod.pixelmon.Pixelmon");
        if (!pixelmonPresent) getLogger().warning("Pixelmon not detected. Features require Pixelmon at runtime.");
        if (getCommand("포켓사진") != null) getCommand("포켓사진").setExecutor(new PocketPhotoCommand(this));
        Bukkit.getPluginManager().registerEvents(new PhotoItemListener(this), this);
        getLogger().info("[PocketPhoto] Enabled v" + getDescription().getVersion());
    }

    private boolean classExists(String name) {
        try { Class.forName(name); return true; } catch (Throwable t) { return false; }
    }
    public static Main get(){ return inst; }
    public boolean isPixelmonPresent(){ return pixelmonPresent; }
}
