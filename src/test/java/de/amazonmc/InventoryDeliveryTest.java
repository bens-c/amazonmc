package de.amazonmc;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class InventoryDeliveryTest {
    private ServerMock server;
    @BeforeEach void setup() { server = MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void stacksAreSplitAndExistingStacksFilled() throws IOException {
        PlayerInventory inventory = empty();
        inventory.setItem(0, new ItemStack(Material.BREAD, 60));
        AtomicBoolean committed = new AtomicBoolean();
        InventoryDelivery.deliver(inventory, new ItemStack(Material.BREAD), 80, () -> committed.set(true));
        assertTrue(committed.get());
        assertEquals(64, inventory.getItem(0).getAmount());
        assertEquals(64, inventory.getItem(1).getAmount());
        assertEquals(12, inventory.getItem(2).getAmount());
    }
    @Test void fullInventoryDoesNotConsumeParcelOrChangeItems() {
        PlayerInventory inventory = full();
        AtomicBoolean committed = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> InventoryDelivery.deliver(inventory,
            new ItemStack(Material.BREAD), 16, () -> committed.set(true)));
        assertFalse(committed.get());
        for (int i = 0; i < 36; i++) assertEquals(new ItemStack(Material.STONE, 64), inventory.getItem(i));
    }
    @Test void insufficientPartialCapacityLeavesAllSlotsUnchanged() {
        PlayerInventory inventory = full();
        inventory.setItem(0, new ItemStack(Material.BREAD, 63));
        assertThrows(IllegalArgumentException.class, () -> InventoryDelivery.deliver(inventory, new ItemStack(Material.BREAD), 2,
            () -> fail("Should not commit")));
        assertEquals(63, inventory.getItem(0).getAmount());
    }
    @Test void failedAccountSaveDoesNotDeliverItems() {
        PlayerInventory inventory = empty();
        assertThrows(IOException.class, () -> InventoryDelivery.deliver(inventory, new ItemStack(Material.DIAMOND), 1,
            () -> { throw new IOException("disk full"); }));
        assertTrue(Arrays.stream(inventory.getStorageContents()).allMatch(InventoryDeliveryTest::isEmpty));
    }
    @Test void nonStackableItemsNeedOneSlotEach() throws IOException {
        PlayerInventory inventory = empty();
        InventoryDelivery.deliver(inventory, new ItemStack(Material.IRON_PICKAXE), 16, () -> { });
        for (int i = 0; i < 16; i++) assertEquals(1, inventory.getItem(i).getAmount());
        assertTrue(isEmpty(inventory.getItem(16)));
    }
    @Test void customComponentsAreNotOverwrittenOrMerged() throws IOException {
        PlayerInventory inventory = empty();
        ItemStack named = new ItemStack(Material.BREAD, 5);
        var meta = named.getItemMeta();
        meta.displayName(Component.text("Mein Brot"));
        named.setItemMeta(meta);
        inventory.setItem(0, named);
        InventoryDelivery.deliver(inventory, new ItemStack(Material.BREAD), 16, () -> { });
        assertEquals(named, inventory.getItem(0));
        assertEquals(16, inventory.getItem(1).getAmount());
        assertFalse(inventory.getItem(1).getItemMeta().hasDisplayName());
    }
    @Test void equipmentSlotsAreNotUsedAsCapacity() {
        PlayerInventory inventory = full();
        assertThrows(IllegalArgumentException.class, () -> InventoryDelivery.deliver(inventory, new ItemStack(Material.BREAD), 1,
            () -> fail("Equipment is not delivery space")));
        for (int i = 36; i < 41; i++) assertTrue(isEmpty(inventory.getItem(i)));
    }
    @Test void catalogHasUniqueIdsAndNoDirectBuySellProfit() {
        assertEquals(28, Catalog.ALL.size());
        assertEquals(Catalog.ALL.size(), Catalog.ALL.stream().map(Catalog.Product::itemId).distinct().count());
        for (var product : Catalog.ALL) {
            assertTrue(product.price() > 0);
            assertTrue(product.amount() >= 1 && product.amount() <= 64);
            assertTrue(product.sellEach() * product.amount() < product.price());
        }
    }
    private static boolean isEmpty(ItemStack stack) { return stack == null || stack.getType().isAir(); }
    private PlayerInventory empty() { return server.addPlayer().getInventory(); }
    private PlayerInventory full() {
        PlayerInventory result = empty();
        for (int i = 0; i < 36; i++) result.setItem(i, new ItemStack(Material.STONE, 64));
        return result;
    }
}
