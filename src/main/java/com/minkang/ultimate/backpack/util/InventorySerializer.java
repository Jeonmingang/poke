package com.minkang.ultimate.backpack.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class InventorySerializer {
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeInt(items.length);
        for (int i = 0; i < items.length; i++) dataOutput.writeObject(items[i]);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack[] items = new ItemStack[dataInput.readInt()];
        for (int i = 0; i < items.length; i++) {
            try {
                Object obj = dataInput.readObject();
                items[i] = (obj instanceof ItemStack) ? (ItemStack)obj : null;
            } catch (ClassNotFoundException e) { items[i] = null; }
        }
        dataInput.close();
        return items;
    }
}
