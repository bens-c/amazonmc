package de.amazonmc;

import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;

/** Plans the full transfer using storage slots only (no armor or offhand). */
final class InventoryDelivery {
    @FunctionalInterface interface Commit { void run() throws IOException; }
    static void deliver(PlayerInventory inventory, ItemStack sample, int count, Commit commit) throws IOException {
        if (sample.getType().isAir() || count < 1) throw new IllegalArgumentException("Ungültiges Paket.");
        ItemStack[] plan = inventory.getStorageContents();
        for (int i = 0; i < plan.length; i++) if (plan[i] != null) plan[i] = plan[i].clone();
        int remaining = count;
        int limit = Math.min(sample.getMaxStackSize(), inventory.getMaxStackSize());
        for (ItemStack slot : plan) {
            if (slot != null && !slot.getType().isAir() && slot.isSimilar(sample)) {
                int n = Math.min(remaining, Math.max(0, limit - slot.getAmount()));
                slot.setAmount(slot.getAmount() + n);
                remaining -= n;
            }
        }
        for (int i = 0; i < plan.length && remaining > 0; i++) {
            if (plan[i] == null || plan[i].getType().isAir()) {
                int n = Math.min(remaining, limit);
                plan[i] = sample.clone();
                plan[i].setAmount(n);
                remaining -= n;
            }
        }
        if (remaining > 0) throw new IllegalArgumentException("Nicht genug Platz! Mach Platz für " + count + " Items. Das Paket bleibt im Fach.");
        commit.run();
        inventory.setStorageContents(plan);
    }
    private InventoryDelivery() { }
}
