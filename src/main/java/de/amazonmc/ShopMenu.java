package de.amazonmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.*;

/** Vanilla chest UI; all actions run after the cancelled Paper inventory event. */
public final class ShopMenu implements InventoryHolder {
    private enum View { SHOP, CONFIRM, PARCELS, SELL }
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final Inventory display;
    private final AmazonPlugin plugin;
    private final Player owner;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private View view;
    private Catalog.Category category = Catalog.Category.ALL;
    private Catalog.Product selected;
    private ItemStack saleSnapshot = new ItemStack(Material.AIR);
    private int bundles = 1;
    private int page;
    private boolean actionQueued;

    public ShopMenu(AmazonPlugin plugin, Player owner, boolean parcels) {
        this.plugin = plugin;
        this.owner = owner;
        this.view = parcels ? View.PARCELS : View.SHOP;
        this.display = plugin.getServer().createInventory(this, 54, Component.text("AmazonMC | Dein Versandhaus"));
        refresh();
    }
    @Override public Inventory getInventory() { return display; }
    void queueClick(Player player, int slot) {
        if (player != owner || actionQueued || slot < 0 || slot >= 54) return;
        Runnable action = actions.get(slot);
        if (action == null) return;
        actionQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            actionQueued = false;
            if (!owner.isOnline() || owner.isDead() || !owner.hasPermission("amazonmc.use")
                || owner.getOpenInventory().getTopInventory() != display) return;
            action.run();
            if (owner.getOpenInventory().getTopInventory() == display) {
                refresh();
                owner.updateInventory();
            }
        });
    }
    void refresh() {
        actions.clear();
        for (int i = 0; i < 54; i++) display.setItem(i, icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        try {
            Ledger.Snapshot account = plugin.ledger().snapshot(owner.getUniqueId());
            if (view == View.SHOP) shop();
            else if (view == View.CONFIRM) confirmation();
            else if (view == View.SELL) sellConfirmation();
            else parcels(account);
            display.setItem(48, icon(Material.GOLD_NUGGET, account.coins() + " Coins", NamedTextColor.GOLD,
                "Dein persönliches Shop-Guthaben", "Kein echtes Geld. Nur für diesen Server."));
            button(45, Material.CHEST, "Zum Shop", NamedTextColor.YELLOW, () -> { view = View.SHOP; }, "Artikel entdecken");
            button(46, Material.SUNFLOWER, "Täglicher Bonus: +100 Coins", NamedTextColor.YELLOW,
                () -> plugin.perform(owner, plugin::bonus), "Alle 24 Stunden abholbar", account.nextBonus() <= plugin.ledger().now() ? "Jetzt verfügbar!" : "Noch nicht wieder verfügbar");
            button(49, Material.BARREL, "Deine Pakete (" + account.parcels().size() + ")", NamedTextColor.GOLD,
                () -> { view = View.PARCELS; page = 0; }, "Nach 30 Sekunden abholbereit", "Bleiben auch nach dem Ausloggen erhalten");
            button(50, Material.EMERALD, "Item aus der Hand verkaufen", NamedTextColor.GREEN,
                () -> { saleSnapshot = owner.getInventory().getItemInMainHand().clone(); view = View.SELL; }, "Zeigt vor dem Verkauf den Preis", "Zum Wechseln des Items Menü schließen");
            button(53, Material.BARRIER, "Schließen", NamedTextColor.RED, owner::closeInventory, "Inventar wieder freigeben");
        } catch (IOException e) {
            actions.clear();
            display.setItem(22, icon(Material.BARRIER, "Shop-Speicher nicht verfügbar", NamedTextColor.RED, "Bitte den Server-Admin informieren."));
            button(53, Material.BARRIER, "Schließen", NamedTextColor.RED, owner::closeInventory);
        }

    }

    private void shop() {
        category(0, Material.CHEST, "Alles", Catalog.Category.ALL);
        category(2, Material.OAK_PLANKS, "Bauen", Catalog.Category.BUILD);
        category(4, Material.IRON_INGOT, "Rohstoffe", Catalog.Category.MATERIAL);
        category(6, Material.BREAD, "Essen", Catalog.Category.FOOD);
        category(8, Material.IRON_PICKAXE, "Ausrüstung", Catalog.Category.GEAR);
        List<Catalog.Product> products = Catalog.ALL.stream().filter(p -> category == Catalog.Category.ALL || p.category() == category).toList();
        for (int i = 0; i < products.size(); i++) {
            Catalog.Product product = products.get(i);
            button(CONTENT[i], product.item(), product.amount() + "× " + product.name(), NamedTextColor.WHITE,
                () -> { selected = product; bundles = 1; view = View.CONFIRM; },
                "Preis: " + product.price() + " Coins pro Bündel",
                product.sellEach() > 0 ? "Ankauf: " + product.sellEach() + " Coins pro Item" : "Kein Ankauf für dieses Item",
                "Lieferzeit: 30 Sekunden", "Linksklick: Bestellung ansehen");
        }
    }

    private void category(int slot, Material item, String name, Catalog.Category value) {
        button(slot, item, (category == value ? "▶ " : "") + name, category == value ? NamedTextColor.GOLD : NamedTextColor.WHITE,
            () -> category = value, "Kategorie auswählen");
    }

    private void confirmation() {
        display.setItem(4, icon(Material.PAPER, "Bestellung prüfen", NamedTextColor.GOLD, "Erst der grüne Knopf kauft den Artikel."));
        display.setItem(22, icon(selected.item(), selected.name(), NamedTextColor.WHITE,
            "Bündel: " + bundles, "Items gesamt: " + (selected.amount() * bundles),
            "Gesamtpreis: " + ((long) selected.price() * bundles) + " Coins", "Lieferung in 30 Sekunden ins Paketfach"));
        button(20, Material.RED_STAINED_GLASS_PANE, "− 1 Bündel", NamedTextColor.RED, () -> bundles = Math.max(1, bundles - 1));
        button(24, Material.LIME_STAINED_GLASS_PANE, "+ 1 Bündel", NamedTextColor.GREEN, () -> bundles = Math.min(16, bundles + 1));
        button(30, Material.ARROW, "Abbrechen", NamedTextColor.RED, () -> view = View.SHOP);
        button(32, Material.LIME_CONCRETE, "Für " + ((long) selected.price() * bundles) + " Coins bestellen", NamedTextColor.GREEN,
            () -> { if (plugin.perform(owner, p -> plugin.buy(p, selected, bundles))) { view = View.PARCELS; page = 0; } },
            "Verbindlich mit Spielgeld bestellen", "Kein Echtgeld, kein Amazon-Konto");
    }

    private void sellConfirmation() {
        Catalog.Product product = Catalog.byItem(saleSnapshot.getType());
        display.setItem(4, icon(Material.EMERALD, "Verkauf prüfen", NamedTextColor.GOLD));
        if (saleSnapshot.getType().isAir() || product == null || product.sellEach() < 1) {
            display.setItem(22, icon(Material.BARRIER, "Kein verkaufbares Item in der Haupthand", NamedTextColor.RED,
                "Menü schließen und ein Shop-Item halten.", "Ankaufspreise stehen bei den Artikeln."));
            return;
        }
        display.setItem(22, icon(product.item(), saleSnapshot.getAmount() + "× " + product.name(), NamedTextColor.WHITE,
            "Du erhältst " + ((long) saleSnapshot.getAmount() * product.sellEach()) + " Coins.", "Der gesamte Stapel in der Haupthand wird verkauft."));
        button(30, Material.ARROW, "Abbrechen", NamedTextColor.RED, () -> view = View.SHOP);
        button(32, Material.LIME_CONCRETE, "Jetzt verkaufen", NamedTextColor.GREEN, () -> {
            if (plugin.perform(owner, p -> {
                if (!saleSnapshot.equals(p.getInventory().getItemInMainHand())) throw new IllegalArgumentException("Item hat sich geändert. Bitte Verkauf erneut öffnen.");
                plugin.sell(p, saleSnapshot.getAmount());
            })) view = View.SHOP;
        }, "Nur unveränderte Standard-Items werden angenommen.");
    }

    private void parcels(Ledger.Snapshot account) throws IOException {
        List<Ledger.Parcel> orders = account.parcels();
        int lastPage = Math.max(0, (orders.size() - 1) / CONTENT.length);
        page = Math.max(0, Math.min(page, lastPage));
        display.setItem(4, icon(Material.BARREL, "Paketfach · Seite " + (page + 1) + "/" + (lastPage + 1), NamedTextColor.GOLD,
            "Grüne Pakete anklicken zum Auspacken", "Bei vollem Inventar bleibt das Paket hier."));
        if (orders.isEmpty()) display.setItem(22, icon(Material.MINECART, "Noch keine Pakete", NamedTextColor.GRAY, "Bestelle etwas im Shop!"));
        long now = plugin.ledger().now();
        for (int i = 0; i < CONTENT.length && page * CONTENT.length + i < orders.size(); i++) {
            Ledger.Parcel parcel = orders.get(page * CONTENT.length + i);
            boolean ready = now >= parcel.readyAt();
            Catalog.Product product = Catalog.ALL.stream().filter(p -> p.itemId().equals(parcel.item())).findFirst().orElse(null);
            String name = product == null ? parcel.item() : product.name();
            button(CONTENT[i], ready ? Material.CHEST : Material.CLOCK, parcel.count() + "× " + name,
                ready ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                () -> plugin.perform(owner, p -> plugin.collect(p, parcel.id())),
                ready ? "Angekommen! Linksklick zum Auspacken." : "Unterwegs: noch " + ((parcel.readyAt() - now + 999) / 1000) + " Sekunden",
                "Bestellung #" + parcel.id().toString().substring(0, 8));
        }
        if (page > 0) button(47, Material.ARROW, "Vorherige Seite", NamedTextColor.WHITE, () -> page--);
        if (page < lastPage) button(51, Material.ARROW, "Nächste Seite", NamedTextColor.WHITE, () -> page++);
    }

    private void button(int slot, Material item, String name, NamedTextColor color, Runnable action, String... lore) {
        display.setItem(slot, icon(item, name, color, lore));
        actions.put(slot, action);
    }


    private static ItemStack icon(Material item, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(item);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(Arrays.stream(lore).map(line -> (Component) Component.text(line, NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
