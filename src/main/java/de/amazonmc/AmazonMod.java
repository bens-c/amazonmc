package de.amazonmc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AmazonMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("amazonmc");
    private Ledger ledger;
    private int ticks;
    private final Map<UUID, Integer> readyCounts = new HashMap<>();

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ledger = new Ledger(server.getWorldPath(LevelResource.ROOT).resolve("amazonmc/accounts"), Clock.systemUTC());
                LOGGER.info("AmazonMC bereit: {} Artikel, /amazon öffnet den Shop.", Catalog.ALL.size());
            } catch (IOException e) {
                LOGGER.error("AmazonMC konnte seine Konten nicht öffnen. Shop bleibt gesperrt.", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ledger = null;
            readyCounts.clear();
            ticks = 0;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            message(handler.getPlayer(), "Willkommen bei AmazonMC! /amazon öffnet den Shop. Dein erstes Konto startet mit 250 Coins.", ChatFormatting.GOLD));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> readyCounts.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ledger == null || ++ticks % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    int ready = (int) ledger.snapshot(player.getUUID()).parcels().stream().filter(p -> p.readyAt() <= ledger.now()).count();
                    int previous = readyCounts.getOrDefault(player.getUUID(), 0);
                    readyCounts.put(player.getUUID(), ready);
                    if (ready > previous) message(player, "Paket angekommen! " + ready + " Paket(e) warten unter /amazon pakete.", ChatFormatting.GREEN);
                    if (player.containerMenu instanceof ShopMenu menu) menu.refresh();
                } catch (IOException e) {
                    // Broken accounts stay unavailable; report only when the player attempts a transaction.
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var root = dispatcher.register(Commands.literal("amazon")
                .executes(command(player -> open(player, false)))
                .then(Commands.literal("shop").executes(command(player -> open(player, false))))
                .then(Commands.literal("pakete").executes(command(player -> open(player, true))))
                .then(Commands.literal("geld").executes(command(player ->
                    message(player, "Dein Guthaben: " + ledger().snapshot(player.getUUID()).coins() + " Coins.", ChatFormatting.GOLD))))
                .then(Commands.literal("bonus").executes(command(this::bonus)))
                .then(Commands.literal("verkaufen")
                    .executes(command(player -> sell(player, player.getMainHandItem().getCount())))
                    .then(Commands.argument("anzahl", IntegerArgumentType.integer(1, 64)).executes(ctx ->
                        guarded(ctx.getSource(), player -> sell(player, IntegerArgumentType.getInteger(ctx, "anzahl"))))))
                .then(Commands.literal("hilfe").executes(command(player -> {
                    message(player, "/amazon – Shop | /amazon pakete – Lieferungen | /amazon geld – Kontostand", ChatFormatting.YELLOW);
                    message(player, "/amazon bonus – 100 Coins alle 24h | /amazon verkaufen [anzahl] – Item in der Haupthand verkaufen", ChatFormatting.YELLOW);
                    message(player, "Artikel anklicken → Menge wählen → grün bestätigen. Lieferung nach 30 Sekunden. Kein echtes Geld.", ChatFormatting.GRAY);
                }))));
            dispatcher.register(Commands.literal("amz").executes(command(player -> open(player, false))).redirect(root));
        });
        LOGGER.info("AmazonMC initialisiert (Fabric 1.21.11).");
    }

    Ledger ledger() throws IOException {
        if (ledger == null) throw new IOException("Shop-Speicher nicht verfügbar.");
        return ledger;
    }

    void open(ServerPlayer player, boolean parcels) throws IOException {
        ledger().snapshot(player.getUUID());
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
            new ShopMenu(id, inventory, this, player, parcels), Component.literal("AmazonMC | Dein Versandhaus")));
    }

    void bonus(ServerPlayer player) throws IOException {
        ledger().daily(player.getUUID());
        message(player, "+100 Coins! Deinen nächsten Bonus gibt es in 24 Stunden.", ChatFormatting.GREEN);
    }

    void buy(ServerPlayer player, Catalog.Product product, int bundles) throws IOException {
        if (bundles < 1 || bundles > 16) throw new IllegalArgumentException("Ungültige Menge.");
        ledger().buy(player.getUUID(), product.itemId(), Math.multiplyExact(product.amount(), bundles), (long) product.price() * bundles);
        message(player, "Bestellt: " + (product.amount() * bundles) + "× " + product.name() + ". Dein Paket kommt in 30 Sekunden!", ChatFormatting.GREEN);
    }

    void sell(ServerPlayer player, int amount) throws IOException {
        ItemStack stack = player.getMainHandItem();
        Catalog.Product product = Catalog.byItem(stack.getItem());
        if (product == null || product.sellEach() == 0) throw new IllegalArgumentException("Dieses Item kaufen wir nicht an. Ankaufspreise stehen im Shop.");
        if (amount < 1 || amount > stack.getCount()) throw new IllegalArgumentException("Du hast nicht genug davon in der Haupthand.");
        if (!ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem())))
            throw new IllegalArgumentException("Nur normale Items ohne Namen, Verzauberungen oder veränderte Daten verkaufen.");
        long value = (long) amount * product.sellEach();
        // Restore the hand if persistence fails. Server-thread serialization prevents concurrent clicks.
        ItemStack before = stack.copy();
        stack.shrink(amount);
        try { ledger().credit(player.getUUID(), value); }
        catch (IOException | RuntimeException e) { player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, before); throw e; }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.level().getServer().getPlayerList().save(player);
        message(player, "Verkauft: " + amount + "× " + product.name() + " für " + value + " Coins.", ChatFormatting.GREEN);
    }

    void collect(ServerPlayer player, UUID parcelId) throws IOException {
        Ledger.Parcel parcel = ledger().readyParcel(player.getUUID(), parcelId);
        Catalog.Product product = Catalog.ALL.stream().filter(p -> p.itemId().equals(parcel.item())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Paketartikel unbekannt. Bitte Server-Admin fragen."));
        InventoryDelivery.deliver(player.getInventory(), new ItemStack(product.item()), parcel.count(),
            () -> ledger().collect(player.getUUID(), parcelId));
        player.inventoryMenu.broadcastChanges();
        player.level().getServer().getPlayerList().save(player);
        message(player, "Paket ausgepackt: " + parcel.count() + "× " + product.name() + ". Viel Spaß!", ChatFormatting.GREEN);
    }

    static void message(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[AmazonMC] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(text).withStyle(color)));
    }

    @FunctionalInterface interface PlayerAction { void run(ServerPlayer player) throws IOException; }
    private Command<CommandSourceStack> command(PlayerAction action) { return ctx -> guarded(ctx.getSource(), action); }
    private int guarded(CommandSourceStack source, PlayerAction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return perform(source.getPlayerOrException(), action) ? 1 : 0;
    }
    boolean perform(ServerPlayer player, PlayerAction action) {
        try { action.run(player); return true; }
        catch (IllegalArgumentException e) { message(player, e.getMessage(), ChatFormatting.RED); }
        catch (IOException e) {
            LOGGER.error("AmazonMC-Speicherfehler für {}", player.getUUID(), e);
            message(player, "Speicherfehler. Aktion abgebrochen – bitte den Server-Admin informieren.", ChatFormatting.RED);
        }
        return false;
    }
}
