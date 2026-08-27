package de.amazonmc;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.nio.file.*;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AmazonPluginTest {
    @TempDir Path directory;
    private ServerMock server;
    private AmazonPlugin plugin;
    private SavingPlayerMock player;
    // MockBukkit does not implement disk player saves. Track the request only;
    // actual Minecraft playerdata persistence still needs a live-server test.
    private static class SavingPlayerMock extends PlayerMock {
        int saves;
        SavingPlayerMock(ServerMock server) { super(server, "ShopTester"); }
        @Override public void saveData() { saves++; }
    }
    private final TestClock clock = new TestClock();
    private static class TestClock extends Clock {
        long millis = 1_000_000_000L;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis); }
    }
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(AmazonPlugin.class);
        var field = AmazonPlugin.class.getDeclaredField("ledger");
        field.setAccessible(true);
        field.set(plugin, new Ledger(directory, clock));
        player = new SavingPlayerMock(server);
        server.addPlayer(player);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    private Ledger.Snapshot account() throws Exception { return plugin.ledger().snapshot(player.getUniqueId()); }
    private void tick() { server.getScheduler().performOneTick(); }
    private InventoryClickEvent click(int slot, ClickType type) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }
    private void left(int slot) { assertTrue(click(slot, ClickType.LEFT).isCancelled()); tick(); }
    private void open() {
        assertTrue(server.dispatchCommand(player, "amazon"));
        assertInstanceOf(ShopMenu.class, player.getOpenInventory().getTopInventory().getHolder());
    }
    @Test void commandOpensMenuAndOnlyConfirmationChargesOnce() throws Exception {
        assertTrue(plugin.isEnabled());
        open();
        left(10);
        assertEquals(250, account().coins());
        assertTrue(account().parcels().isEmpty());
        assertTrue(click(32, ClickType.LEFT).isCancelled());
        assertTrue(click(32, ClickType.LEFT).isCancelled());
        tick();
        assertEquals(186, account().coins());
        assertEquals(1, account().parcels().size());
        assertEquals(16, account().parcels().getFirst().count());
    }
    @Test void specialClicksAndLowerInventoryCannotTakeIconsOrOrder() throws Exception {
        open();
        for (ClickType type : new ClickType[] {ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.RIGHT,
                ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DROP, ClickType.CONTROL_DROP,
                ClickType.DOUBLE_CLICK, ClickType.MIDDLE, ClickType.CREATIVE}) {
            assertTrue(click(10, type).isCancelled());
            tick();
        }
        assertTrue(click(60, ClickType.SHIFT_LEFT).isCancelled());
        assertTrue(click(60, ClickType.LEFT).isCancelled());
        tick();
        assertEquals(Material.OAK_LOG, player.getOpenInventory().getTopInventory().getItem(10).getType());
        assertEquals(250, account().coins());
        assertTrue(account().parcels().isEmpty());
    }
    @Test void dragsAreCancelledInBothInventories() {
        open();
        for (int slot : new int[] {10, 60}) {
            var event = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.AIR),
                new ItemStack(Material.STONE), false, Map.of(slot, new ItemStack(Material.STONE)));
            server.getPluginManager().callEvent(event);
            assertTrue(event.isCancelled());
        }
    }
    @Test void closingMenuBeforeQueuedConfirmationPreventsPurchase() throws Exception {
        open(); left(10);
        click(32, ClickType.LEFT);
        player.closeInventory();
        tick();
        assertEquals(250, account().coins());
        assertTrue(account().parcels().isEmpty());
    }
    @Test void revokedPermissionBlocksQueuedConfirmationAndCommands() throws Exception {
        open(); left(10);
        click(32, ClickType.LEFT);
        player.addAttachment(plugin, "amazonmc.use", false);
        tick();
        server.dispatchCommand(player, "amazon bonus");
        assertEquals(250, account().coins());
        assertTrue(account().parcels().isEmpty());
    }
    @Test void previouslyCancelledClicksDoNotRunActions() throws Exception {
        open(); left(10);
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            32, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        tick();
        assertEquals(250, account().coins());
    }
    @Test void dailyCommandAndAliasCannotClaimTwice() throws Exception {
        server.dispatchCommand(player, "amazon bonus");
        server.dispatchCommand(player, "amz daily");
        assertEquals(350, account().coins());
        clock.millis += Ledger.DAILY_MS;
        server.dispatchCommand(player, "amz daily");
        assertEquals(450, account().coins());
    }
    @Test void saleRemovesOnlyRequestedAmountAndRejectsNamedItems() throws Exception {
        player.getInventory().setItemInMainHand(new ItemStack(Material.BREAD, 16));
        server.dispatchCommand(player, "amazon verkaufen 8");
        assertEquals(266, account().coins());
        assertEquals(8, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, player.saves);
        ItemStack named = new ItemStack(Material.BREAD, 8);
        var meta = named.getItemMeta();
        meta.displayName(Component.text("Keep me"));
        named.setItemMeta(meta);
        player.getInventory().setItemInMainHand(named);
        assertThrows(IllegalArgumentException.class, () -> plugin.sell(player, 8));
        assertEquals(named, player.getInventory().getItemInMainHand());
        assertEquals(266, account().coins());
    }
    @Test void invalidSaleAmountsLeaveBalanceAndItemsUnchanged() throws Exception {
        player.getInventory().setItemInMainHand(new ItemStack(Material.BREAD, 16));
        for (String amount : new String[] {"0", "-1", "65", "17", "nope", "99999999999999"})
            server.dispatchCommand(player, "amazon sell " + amount);
        assertEquals(250, account().coins());
        assertEquals(16, player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void salePersistenceFailureRestoresHeldItems() throws Exception {
        account();
        Path destination = directory.resolve(player.getUniqueId() + ".json");
        Files.delete(destination);
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("blocker"), "keep");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BREAD, 16));
        assertThrows(java.io.IOException.class, () -> plugin.sell(player, 8));
        assertEquals(16, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(250, account().coins());
    }
    @Test void changedHeldStackInvalidatesSaleConfirmation() throws Exception {
        player.getInventory().setItemInMainHand(new ItemStack(Material.BREAD, 16));
        open(); left(50);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));
        left(32);
        assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
        assertEquals(250, account().coins());
    }
    @Test void parcelWaitsForDeliveryAndSpaceAndCanOnlyBeCollectedOnce() throws Exception {
        plugin.buy(player, Catalog.byItem(Material.BREAD), 1);
        var parcel = account().parcels().getFirst();
        assertThrows(IllegalArgumentException.class, () -> plugin.collect(player, parcel.id()));
        clock.millis += Ledger.DELIVERY_MS;
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        assertThrows(IllegalArgumentException.class, () -> plugin.collect(player, parcel.id()));
        assertEquals(1, account().parcels().size());
        player.getInventory().setItem(0, null);
        plugin.collect(player, parcel.id());
        assertEquals(new ItemStack(Material.BREAD, 16), player.getInventory().getItem(0));
        assertTrue(account().parcels().isEmpty());
        assertEquals(1, player.saves);
        assertThrows(IllegalArgumentException.class, () -> plugin.collect(player, parcel.id()));
    }
    @Test void disablingPluginClosesShop() {
        open();
        server.getPluginManager().disablePlugin(plugin);
        var top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || !(top.getHolder() instanceof ShopMenu));
    }
}
