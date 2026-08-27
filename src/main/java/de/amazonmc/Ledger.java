package de.amazonmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;

/** World-local economy. Called only on the server thread. Persist before committing in memory. */
public final class Ledger {
    public static final int START_COINS = 250;
    public static final int DAILY_COINS = 100;
    public static final long DAILY_MS = 86_400_000L;
    public static final long DELIVERY_MS = 30_000L;
    public static final int MAX_ORDERS = 56;
    public static final long MAX_COINS = 1_000_000_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final Clock clock;
    private final Map<UUID, Account> accounts = new HashMap<>();

    public record Parcel(UUID id, String item, int count, long readyAt) { }
    public record Snapshot(long coins, long nextBonus, List<Parcel> parcels) { }
    private static final class Account {
        int schema = 1;
        long coins = START_COINS;
        long nextBonus;
        List<Parcel> parcels = new ArrayList<>();
        Account copy() {
            Account result = new Account();
            result.coins = coins;
            result.nextBonus = nextBonus;
            result.parcels = new ArrayList<>(parcels);
            return result;
        }
    }

    public Ledger(Path directory, Clock clock) throws IOException {
        this.directory = directory;
        this.clock = clock;
        Files.createDirectories(directory);
    }

    public long now() { return clock.millis(); }

    public Snapshot snapshot(UUID player) throws IOException {
        Account account = account(player);
        return new Snapshot(account.coins, account.nextBonus, List.copyOf(account.parcels));
    }

    public Parcel buy(UUID player, String item, int count, long price) throws IOException {
        if (item == null || !item.matches("minecraft:[a-z0-9_]+") || count < 1 || count > 4096
                || price < 1 || price > MAX_COINS) throw new IllegalArgumentException("Ungültige Bestellung.");
        return change(player, a -> {
            if (a.parcels.size() >= MAX_ORDERS) throw new IllegalArgumentException("Paketfach voll! Erst Pakete abholen.");
            if (a.coins < price) throw new IllegalArgumentException("Nicht genug Coins. Dir fehlen " + (price - a.coins) + ".");
            Parcel parcel = new Parcel(UUID.randomUUID(), item, count, now() + DELIVERY_MS);
            a.coins -= price;
            a.parcels.add(parcel);
            return parcel;
        });
    }

    public void credit(UUID player, long amount) throws IOException {
        if (amount < 1 || amount > MAX_COINS) throw new IllegalArgumentException("Ungültiger Betrag.");
        change(player, a -> {
            if (a.coins > MAX_COINS - amount) throw new IllegalArgumentException("Dein Coin-Konto ist voll.");
            a.coins += amount;
            return null;
        });
    }

    public void daily(UUID player) throws IOException {
        change(player, a -> {
            if (now() < a.nextBonus) {
                long minutes = Math.max(1, (a.nextBonus - now() + 59_999) / 60_000);
                throw new IllegalArgumentException("Nächster Bonus in " + minutes + " Minuten.");
            }
            if (a.coins > MAX_COINS - DAILY_COINS) throw new IllegalArgumentException("Dein Coin-Konto ist voll.");
            a.coins += DAILY_COINS;
            a.nextBonus = now() + DAILY_MS;
            return null;
        });
    }

    public Parcel readyParcel(UUID player, UUID id) throws IOException {
        Parcel parcel = account(player).parcels.stream().filter(p -> p.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Dieses Paket wurde bereits abgeholt."));
        if (now() < parcel.readyAt) throw new IllegalArgumentException("Noch unterwegs: " + ((parcel.readyAt - now() + 999) / 1000) + " Sekunden.");
        return parcel;
    }

    /** The caller must check inventory capacity before removing the parcel. */
    public Parcel collect(UUID player, UUID id) throws IOException {
        readyParcel(player, id);
        return change(player, a -> {
            Parcel p = a.parcels.stream().filter(order -> order.id.equals(id)).findFirst().orElseThrow();
            a.parcels.remove(p);
            return p;
        });
    }

    private <T> T change(UUID player, Function<Account, T> mutation) throws IOException {
        Account next = account(player).copy();
        T result = mutation.apply(next);
        save(player, next);
        accounts.put(player, next);
        return result;
    }

    private Account account(UUID player) throws IOException {
        Account cached = accounts.get(player);
        if (cached != null) return cached;
        Path file = directory.resolve(player + ".json");
        Account result;
        if (Files.exists(file)) {
            try {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (String key : List.of("schema", "coins", "nextBonus", "parcels")) {
                    if (!json.has(key) || json.get(key).isJsonNull()) throw new IllegalStateException("Missing " + key);
                }
                for (String key : List.of("schema", "coins", "nextBonus")) {
                    if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber())
                        throw new IllegalStateException("Invalid number " + key);
                    json.get(key).getAsBigDecimal().toBigIntegerExact().longValueExact();
                }
                result = GSON.fromJson(json, Account.class);
                validate(result);
            } catch (RuntimeException e) {
                // Never replace a damaged balance with a fresh starting balance.
                throw new IOException("Beschädigtes AmazonMC-Konto: " + file, e);
            }
        } else {
            result = new Account();
            save(player, result);
        }
        accounts.put(player, result);
        return result;
    }

    private static void validate(Account a) {
        if (a == null || a.schema != 1 || a.coins < 0 || a.coins > MAX_COINS || a.nextBonus < 0
                || a.parcels == null || a.parcels.size() > MAX_ORDERS) throw new IllegalStateException("Invalid account");
        Set<UUID> ids = new HashSet<>();
        for (Parcel p : a.parcels) {
            if (p == null || p.id == null || !ids.add(p.id) || p.item == null
                    || !p.item.matches("minecraft:[a-z0-9_]+") || p.count < 1 || p.count > 4096 || p.readyAt < 0)
                throw new IllegalStateException("Invalid parcel");
        }
    }

    private void save(UUID player, Account account) throws IOException {
        Path destination = directory.resolve(player + ".json");
        Path temp = Files.createTempFile(directory, player + "-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(account), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
