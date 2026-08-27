package de.amazonmc;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;

/** Plans the complete transfer before touching either the account or the inventory. */
final class InventoryDelivery {
    @FunctionalInterface interface Commit { void run() throws IOException; }

    static void deliver(Container inventory, ItemStack sample, int count, Commit commit) throws IOException {
        if (sample.isEmpty() || count < 1 || inventory.getContainerSize() < 36) throw new IllegalArgumentException("Ungültiges Paket.");
        ItemStack[] plan = new ItemStack[36];
        for (int i = 0; i < plan.length; i++) plan[i] = inventory.getItem(i).copy();
        int remaining = count;
        for (ItemStack slot : plan) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, sample)) {
                int n = Math.min(remaining, Math.max(0, Math.min(sample.getMaxStackSize(), inventory.getMaxStackSize()) - slot.getCount()));
                slot.grow(n);
                remaining -= n;
            }
        }
        for (int i = 0; i < plan.length && remaining > 0; i++) {
            if (plan[i].isEmpty()) {
                int n = Math.min(remaining, Math.min(sample.getMaxStackSize(), inventory.getMaxStackSize()));
                plan[i] = sample.copyWithCount(n);
                remaining -= n;
            }
        }
        if (remaining > 0) throw new IllegalArgumentException("Nicht genug Platz! Mach Platz für " + count + " Items. Das Paket bleibt im Fach.");
        commit.run();
        for (int i = 0; i < plan.length; i++) inventory.setItem(i, plan[i]);
        inventory.setChanged();
    }
    private InventoryDelivery() { }
}
