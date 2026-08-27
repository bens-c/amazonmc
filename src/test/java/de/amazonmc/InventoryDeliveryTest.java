package de.amazonmc;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class InventoryDeliveryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    @Test void stacksAreSplitAndExistingStacksFilled() throws IOException {
        SimpleContainer inventory = new SimpleContainer(36);
        inventory.setItem(0, new ItemStack(Items.BREAD, 60));
        AtomicBoolean committed = new AtomicBoolean();
        InventoryDelivery.deliver(inventory, new ItemStack(Items.BREAD), 80, () -> committed.set(true));
        assertTrue(committed.get());
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(64, inventory.getItem(1).getCount());
        assertEquals(12, inventory.getItem(2).getCount());
    }
    @Test void fullInventoryDoesNotConsumeParcelOrChangeItems() {
        SimpleContainer inventory = full();
        AtomicBoolean committed = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> InventoryDelivery.deliver(inventory,
            new ItemStack(Items.BREAD), 16, () -> committed.set(true)));
        assertFalse(committed.get());
        for (int i = 0; i < 36; i++) assertTrue(ItemStack.matches(new ItemStack(Items.STONE, 64), inventory.getItem(i)));
    }
    @Test void insufficientPartialCapacityLeavesAllSlotsUnchanged() {
        SimpleContainer inventory = full();
        inventory.setItem(0, new ItemStack(Items.BREAD, 63));
        assertThrows(IllegalArgumentException.class, () -> InventoryDelivery.deliver(inventory, new ItemStack(Items.BREAD), 2,
            () -> fail("Should not commit")));
        assertEquals(63, inventory.getItem(0).getCount());
    }
    @Test void failedAccountSaveDoesNotDeliverItems() {
        SimpleContainer inventory = new SimpleContainer(36);
        assertThrows(IOException.class, () -> InventoryDelivery.deliver(inventory, new ItemStack(Items.DIAMOND), 1,
            () -> { throw new IOException("disk full"); }));
        assertTrue(inventory.isEmpty());
    }
    @Test void nonStackableItemsNeedOneSlotEach() throws IOException {
        SimpleContainer inventory = new SimpleContainer(36);
        InventoryDelivery.deliver(inventory, new ItemStack(Items.IRON_PICKAXE), 16, () -> { });
        for (int i = 0; i < 16; i++) assertEquals(1, inventory.getItem(i).getCount());
        assertTrue(inventory.getItem(16).isEmpty());
    }
    @Test void customComponentsAreNotOverwrittenOrMerged() throws IOException {
        SimpleContainer inventory = new SimpleContainer(36);
        ItemStack named = new ItemStack(Items.BREAD, 5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Mein Brot"));
        inventory.setItem(0, named);
        InventoryDelivery.deliver(inventory, new ItemStack(Items.BREAD), 16, () -> { });
        assertTrue(ItemStack.matches(named, inventory.getItem(0)));
        assertEquals(16, inventory.getItem(1).getCount());
        assertNull(inventory.getItem(1).get(DataComponents.CUSTOM_NAME));
    }
    @Test void equipmentSlotsAreNotUsedAsCapacity() {
        SimpleContainer inventory = new SimpleContainer(41);
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        assertThrows(IllegalArgumentException.class, () -> InventoryDelivery.deliver(inventory, new ItemStack(Items.BREAD), 1,
            () -> fail("Equipment is not delivery space")));
        for (int i = 36; i < 41; i++) assertTrue(inventory.getItem(i).isEmpty());
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
    private static SimpleContainer full() {
        SimpleContainer result = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) result.setItem(i, new ItemStack(Items.STONE, 64));
        return result;
    }
}
