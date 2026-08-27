package de.amazonmc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.io.IOException;
import java.util.*;

/** Uses the vanilla 6-row chest protocol: no client rendering code or resource pack needed. */
public final class ShopMenu extends ChestMenu {
    private enum View { SHOP, CONFIRM, PARCELS, SELL }
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final SimpleContainer display;
    private final AmazonMod mod;
    private final ServerPlayer owner;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private View view;
    private Catalog.Category category = Catalog.Category.ALL;
    private Catalog.Product selected;
    private ItemStack saleSnapshot = ItemStack.EMPTY;
    private int bundles = 1;
    private int page;
    private long nextClick;

    public ShopMenu(int id, Inventory inventory, AmazonMod mod, ServerPlayer owner, boolean parcels) {
        this(id, inventory, new SimpleContainer(54), mod, owner, parcels);
    }
    private ShopMenu(int id, Inventory inventory, SimpleContainer display, AmazonMod mod, ServerPlayer owner, boolean parcels) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.display = display;
        this.mod = mod;
        this.owner = owner;
        this.view = parcels ? View.PARCELS : View.SHOP;
        refresh();
    }

    @Override public boolean stillValid(Player player) { return player == owner && owner.isAlive(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) { return false; }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        // Never delegate to ChestMenu: display stacks are buttons, not obtainable items.
        // Blocks shift-click, number-key swaps, drag, drop, offhand swap, double-click and creative clone.
        if (player != owner) return;
        if (type == ClickType.PICKUP && button == 0 && slot >= 0 && slot < 54
                && System.nanoTime() >= nextClick) {
            nextClick = System.nanoTime() + 250_000_000L;
            Runnable action = actions.get(slot);
            if (action != null) action.run();
        }
        refresh();
        // Undo the vanilla client's predicted cursor and slot movements, even for rejected clicks.
        sendAllDataToRemote();
    }

    void refresh() {
        actions.clear();
        for (int i = 0; i < 54; i++) display.setItem(i, icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.GRAY));
        try {
            Ledger.Snapshot account = mod.ledger().snapshot(owner.getUUID());
            if (view == View.SHOP) shop();
            else if (view == View.CONFIRM) confirmation();
            else if (view == View.SELL) sellConfirmation();
            else parcels(account);
            display.setItem(48, icon(Items.GOLD_NUGGET, account.coins() + " Coins", ChatFormatting.GOLD,
                "Dein persönliches Shop-Guthaben", "Kein echtes Geld. Nur für diese Welt."));
            button(45, Items.CHEST, "Zum Shop", ChatFormatting.YELLOW, () -> { view = View.SHOP; }, "Artikel entdecken");
            button(46, Items.SUNFLOWER, "Täglicher Bonus: +100 Coins", ChatFormatting.YELLOW,
                () -> mod.perform(owner, mod::bonus), "Alle 24 Stunden abholbar", account.nextBonus() <= mod.ledger().now() ? "Jetzt verfügbar!" : "Noch nicht wieder verfügbar");
            button(49, Items.BARREL, "Deine Pakete (" + account.parcels().size() + ")", ChatFormatting.GOLD,
                () -> { view = View.PARCELS; page = 0; }, "Nach 30 Sekunden abholbereit", "Bleiben auch nach dem Ausloggen erhalten");
            button(50, Items.EMERALD, "Item aus der Hand verkaufen", ChatFormatting.GREEN,
                () -> { saleSnapshot = owner.getMainHandItem().copy(); view = View.SELL; }, "Zeigt vor dem Verkauf den Preis", "Zum Wechseln des Items Menü schließen");
            button(53, Items.BARRIER, "Schließen", ChatFormatting.RED, owner::closeContainer, "Inventar wieder freigeben");
        } catch (IOException e) {
            actions.clear();
            display.setItem(22, icon(Items.BARRIER, "Shop-Speicher nicht verfügbar", ChatFormatting.RED, "Bitte den Server-Admin informieren."));
            button(53, Items.BARRIER, "Schließen", ChatFormatting.RED, owner::closeContainer);
        }
        broadcastChanges();
    }

    private void shop() {
        category(0, Items.CHEST, "Alles", Catalog.Category.ALL);
        category(2, Items.OAK_PLANKS, "Bauen", Catalog.Category.BUILD);
        category(4, Items.IRON_INGOT, "Rohstoffe", Catalog.Category.MATERIAL);
        category(6, Items.BREAD, "Essen", Catalog.Category.FOOD);
        category(8, Items.IRON_PICKAXE, "Ausrüstung", Catalog.Category.GEAR);
        List<Catalog.Product> products = Catalog.ALL.stream().filter(p -> category == Catalog.Category.ALL || p.category() == category).toList();
        for (int i = 0; i < products.size(); i++) {
            Catalog.Product product = products.get(i);
            button(CONTENT[i], product.item(), product.amount() + "× " + product.name(), ChatFormatting.WHITE,
                () -> { selected = product; bundles = 1; view = View.CONFIRM; },
                "Preis: " + product.price() + " Coins pro Bündel",
                product.sellEach() > 0 ? "Ankauf: " + product.sellEach() + " Coins pro Item" : "Kein Ankauf für dieses Item",
                "Lieferzeit: 30 Sekunden", "Linksklick: Bestellung ansehen");
        }
    }

    private void category(int slot, Item item, String name, Catalog.Category value) {
        button(slot, item, (category == value ? "▶ " : "") + name, category == value ? ChatFormatting.GOLD : ChatFormatting.WHITE,
            () -> category = value, "Kategorie auswählen");
    }

    private void confirmation() {
        display.setItem(4, icon(Items.PAPER, "Bestellung prüfen", ChatFormatting.GOLD, "Erst der grüne Knopf kauft den Artikel."));
        display.setItem(22, icon(selected.item(), selected.name(), ChatFormatting.WHITE,
            "Bündel: " + bundles, "Items gesamt: " + (selected.amount() * bundles),
            "Gesamtpreis: " + ((long) selected.price() * bundles) + " Coins", "Lieferung in 30 Sekunden ins Paketfach"));
        button(20, Items.RED_STAINED_GLASS_PANE, "− 1 Bündel", ChatFormatting.RED, () -> bundles = Math.max(1, bundles - 1));
        button(24, Items.LIME_STAINED_GLASS_PANE, "+ 1 Bündel", ChatFormatting.GREEN, () -> bundles = Math.min(16, bundles + 1));
        button(30, Items.ARROW, "Abbrechen", ChatFormatting.RED, () -> view = View.SHOP);
        button(32, Items.LIME_CONCRETE, "Für " + ((long) selected.price() * bundles) + " Coins bestellen", ChatFormatting.GREEN,
            () -> { if (mod.perform(owner, p -> mod.buy(p, selected, bundles))) { view = View.PARCELS; page = 0; } },
            "Verbindlich mit Spielgeld bestellen", "Kein Echtgeld, kein Amazon-Konto");
    }

    private void sellConfirmation() {
        Catalog.Product product = Catalog.byItem(saleSnapshot.getItem());
        display.setItem(4, icon(Items.EMERALD, "Verkauf prüfen", ChatFormatting.GOLD));
        if (saleSnapshot.isEmpty() || product == null || product.sellEach() < 1) {
            display.setItem(22, icon(Items.BARRIER, "Kein verkaufbares Item in der Haupthand", ChatFormatting.RED,
                "Menü schließen und ein Shop-Item halten.", "Ankaufspreise stehen bei den Artikeln."));
            return;
        }
        display.setItem(22, icon(product.item(), saleSnapshot.getCount() + "× " + product.name(), ChatFormatting.WHITE,
            "Du erhältst " + ((long) saleSnapshot.getCount() * product.sellEach()) + " Coins.", "Der gesamte Stapel in der Haupthand wird verkauft."));
        button(30, Items.ARROW, "Abbrechen", ChatFormatting.RED, () -> view = View.SHOP);
        button(32, Items.LIME_CONCRETE, "Jetzt verkaufen", ChatFormatting.GREEN, () -> {
            if (mod.perform(owner, p -> {
                if (!ItemStack.matches(saleSnapshot, p.getMainHandItem())) throw new IllegalArgumentException("Item hat sich geändert. Bitte Verkauf erneut öffnen.");
                mod.sell(p, saleSnapshot.getCount());
            })) view = View.SHOP;
        }, "Nur unveränderte Standard-Items werden angenommen.");
    }

    private void parcels(Ledger.Snapshot account) throws IOException {
        List<Ledger.Parcel> orders = account.parcels();
        int lastPage = Math.max(0, (orders.size() - 1) / CONTENT.length);
        page = Math.max(0, Math.min(page, lastPage));
        display.setItem(4, icon(Items.BARREL, "Paketfach · Seite " + (page + 1) + "/" + (lastPage + 1), ChatFormatting.GOLD,
            "Grüne Pakete anklicken zum Auspacken", "Bei vollem Inventar bleibt das Paket hier."));
        if (orders.isEmpty()) display.setItem(22, icon(Items.MINECART, "Noch keine Pakete", ChatFormatting.GRAY, "Bestelle etwas im Shop!"));
        long now = mod.ledger().now();
        for (int i = 0; i < CONTENT.length && page * CONTENT.length + i < orders.size(); i++) {
            Ledger.Parcel parcel = orders.get(page * CONTENT.length + i);
            boolean ready = now >= parcel.readyAt();
            Catalog.Product product = Catalog.ALL.stream().filter(p -> p.itemId().equals(parcel.item())).findFirst().orElse(null);
            String name = product == null ? parcel.item() : product.name();
            button(CONTENT[i], ready ? Items.CHEST : Items.CLOCK, parcel.count() + "× " + name,
                ready ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                () -> mod.perform(owner, p -> mod.collect(p, parcel.id())),
                ready ? "Angekommen! Linksklick zum Auspacken." : "Unterwegs: noch " + ((parcel.readyAt() - now + 999) / 1000) + " Sekunden",
                "Bestellung #" + parcel.id().toString().substring(0, 8));
        }
        if (page > 0) button(47, Items.ARROW, "Vorherige Seite", ChatFormatting.WHITE, () -> page--);
        if (page < lastPage) button(51, Items.ARROW, "Nächste Seite", ChatFormatting.WHITE, () -> page++);
    }

    private void button(int slot, Item item, String name, ChatFormatting color, Runnable action, String... lore) {
        display.setItem(slot, icon(item, name, color, lore));
        actions.put(slot, action);
    }
    private static ItemStack icon(Item item, String name, ChatFormatting color, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
            .map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))).toList()));
        return stack;
    }
}
