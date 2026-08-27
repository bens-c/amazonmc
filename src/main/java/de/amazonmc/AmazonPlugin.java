package de.amazonmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.logging.Level;

public class AmazonPlugin extends JavaPlugin implements Listener, TabExecutor {
    private Ledger ledger;
    private BukkitTask ticker;
    private final Map<UUID, Integer> readyCounts = new HashMap<>();
    private static final List<String> COMMANDS = List.of("shop", "pakete", "geld", "bonus", "verkaufen", "hilfe");

    @Override public void onEnable() {
        try { ledger = new Ledger(getDataFolder().toPath().resolve("accounts"), Clock.systemUTC()); }
        catch (IOException e) {
            getLogger().log(Level.SEVERE, "Konten nicht verfügbar. AmazonMC wird deaktiviert.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PluginCommand command = Objects.requireNonNull(getCommand("amazon"), "amazon missing in plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        ticker = getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("AmazonMC bereit (Paper 1.21.11): " + Catalog.ALL.size() + " Artikel. /amazon");
    }
    @Override public void onDisable() {
        if (ticker != null) ticker.cancel();
        for (Player player : getServer().getOnlinePlayers()) {
            if (openMenu(player) != null) player.closeInventory();
        }
        readyCounts.clear();
        ledger = null;
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("amazonmc.use"))
            message(event.getPlayer(), "Willkommen bei AmazonMC! /amazon öffnet den Shop. Neue Konten starten mit 250 Coins.", NamedTextColor.GOLD);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { readyCounts.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopMenu menu)) return;
        boolean alreadyCancelled = event.isCancelled();
        // Also cancels lower-inventory shift clicks, number keys, offhand swaps and creative cloning.
        event.setCancelled(true);
        if (!alreadyCancelled && event.getWhoClicked() instanceof Player player && event.getClick() == ClickType.LEFT)
            menu.queueClick(player, event.getRawSlot());
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu) event.setCancelled(true);
    }
    private ShopMenu openMenu(Player player) {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof ShopMenu menu ? menu : null;
    }
    private void tick() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.hasPermission("amazonmc.use")) {
                if (openMenu(player) != null) player.closeInventory();
                continue;
            }
            try {
                int ready = (int) ledger().snapshot(player.getUniqueId()).parcels().stream().filter(p -> p.readyAt() <= ledger.now()).count();
                int previous = readyCounts.getOrDefault(player.getUniqueId(), 0);
                readyCounts.put(player.getUniqueId(), ready);
                if (ready > previous) message(player, "Paket angekommen! " + ready + " Paket(e) warten unter /amazon pakete.", NamedTextColor.GREEN);
                ShopMenu menu = openMenu(player);
                if (menu != null) menu.refresh();
            } catch (IOException e) { /* Explicit commands report storage errors; damaged accounts stay blocked. */ }
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("AmazonMC: Dieser Befehl ist nur im Spiel verfügbar."); return true; }
        String sub = args.length == 0 ? "shop" : args[0].toLowerCase(Locale.ROOT);
        boolean selling = sub.equals("verkaufen") || sub.equals("sell");
        if (args.length > (selling ? 2 : 1)) { help(player); return true; }
        perform(player, p -> {
            switch (sub) {
                case "shop" -> open(p, false);
                case "pakete", "orders" -> open(p, true);
                case "geld", "balance" -> message(p, "Dein Guthaben: " + ledger().snapshot(p.getUniqueId()).coins() + " Coins.", NamedTextColor.GOLD);
                case "bonus", "daily" -> bonus(p);
                case "verkaufen", "sell" -> {
                    int amount = p.getInventory().getItemInMainHand().getAmount();
                    if (args.length == 2) {
                        try { amount = Integer.parseInt(args[1]); }
                        catch (NumberFormatException e) { throw new IllegalArgumentException("Bitte eine Anzahl von 1 bis 64 angeben."); }
                    }
                    sell(p, amount);
                }
                default -> help(p);
            }
        });
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("amazonmc.use")) return List.of();
        if (args.length == 1) return COMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("verkaufen") || args[0].equalsIgnoreCase("sell")))
            return List.of("1", "8", "16", "32", "64").stream().filter(s -> s.startsWith(args[1])).toList();
        return List.of();
    }
    private void help(Player player) {
        message(player, "/amazon – Shop | /amazon pakete – Lieferungen | /amazon geld – Kontostand", NamedTextColor.YELLOW);
        message(player, "/amazon bonus – 100 Coins alle 24h | /amazon verkaufen [anzahl] – Haupthand sofort verkaufen", NamedTextColor.YELLOW);
        message(player, "Artikel anklicken → Menge wählen → grün bestätigen. Lieferung nach 30 Sekunden. Kein echtes Geld.", NamedTextColor.GRAY);
    }
    Ledger ledger() throws IOException {
        if (ledger == null) throw new IOException("Shop-Speicher nicht verfügbar.");
        return ledger;
    }
    void open(Player player, boolean parcels) throws IOException {
        ledger().snapshot(player.getUniqueId());
        player.openInventory(new ShopMenu(this, player, parcels).getInventory());
    }
    void bonus(Player player) throws IOException {
        ledger().daily(player.getUniqueId());
        message(player, "+100 Coins! Deinen nächsten Bonus gibt es in 24 Stunden.", NamedTextColor.GREEN);
    }
    void buy(Player player, Catalog.Product product, int bundles) throws IOException {
        if (bundles < 1 || bundles > 16) throw new IllegalArgumentException("Ungültige Menge.");
        ledger().buy(player.getUniqueId(), product.itemId(), Math.multiplyExact(product.amount(), bundles), (long) product.price() * bundles);
        message(player, "Bestellt: " + (product.amount() * bundles) + "× " + product.name() + ". Dein Paket kommt in 30 Sekunden!", NamedTextColor.GREEN);
    }
    void sell(Player player, int amount) throws IOException {
        ItemStack stack = player.getInventory().getItemInMainHand();
        Catalog.Product product = Catalog.byItem(stack.getType());
        if (product == null || product.sellEach() == 0) throw new IllegalArgumentException("Dieses Item kaufen wir nicht an. Ankaufspreise stehen im Shop.");
        if (amount < 1 || amount > 64 || amount > stack.getAmount()) throw new IllegalArgumentException("Ungültige Anzahl oder nicht genug Items in der Haupthand.");
        if (!stack.isSimilar(new ItemStack(stack.getType())))
            throw new IllegalArgumentException("Nur normale Items ohne Namen, Verzauberungen oder veränderte Daten verkaufen.");
        ItemStack before = stack.clone();
        ItemStack remainder = stack.clone();
        remainder.setAmount(stack.getAmount() - amount);
        player.getInventory().setItemInMainHand(remainder);
        long value = (long) amount * product.sellEach();
        try { ledger().credit(player.getUniqueId(), value); }
        catch (IOException | RuntimeException e) { player.getInventory().setItemInMainHand(before); throw e; }
        player.saveData();
        message(player, "Verkauft: " + amount + "× " + product.name() + " für " + value + " Coins.", NamedTextColor.GREEN);
    }
    void collect(Player player, UUID parcelId) throws IOException {
        Ledger.Parcel parcel = ledger().readyParcel(player.getUniqueId(), parcelId);
        Catalog.Product product = Catalog.ALL.stream().filter(p -> p.itemId().equals(parcel.item())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Paketartikel unbekannt. Bitte Server-Admin fragen."));
        InventoryDelivery.deliver(player.getInventory(), new ItemStack(product.item()), parcel.count(),
            () -> ledger().collect(player.getUniqueId(), parcelId));
        player.saveData();
        message(player, "Paket ausgepackt: " + parcel.count() + "× " + product.name() + ". Viel Spaß!", NamedTextColor.GREEN);
    }
    static void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text("[AmazonMC] ", NamedTextColor.GOLD).append(Component.text(text, color)));
    }
    @FunctionalInterface interface PlayerAction { void run(Player player) throws IOException; }
    boolean perform(Player player, PlayerAction action) {
        if (!player.hasPermission("amazonmc.use")) { message(player, "Keine Berechtigung: amazonmc.use", NamedTextColor.RED); return false; }
        try { action.run(player); return true; }
        catch (IllegalArgumentException e) { message(player, e.getMessage(), NamedTextColor.RED); }
        catch (IOException e) {
            getLogger().log(Level.SEVERE, "Speicherfehler für " + player.getUniqueId(), e);
            message(player, "Speicherfehler. Aktion abgebrochen – bitte den Server-Admin informieren.", NamedTextColor.RED);
        }
        return false;
    }
}
