package de.amazonmc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LedgerTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private static class TestClock extends Clock {
        long millis = 1_000_000_000L;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis); }
    }
    private final TestClock clock = new TestClock();
    private Ledger ledger() throws IOException { return new Ledger(directory, clock); }

    @Test void initialBalanceIsStorageLocalAndPersistent() throws IOException {
        assertEquals(250, ledger().snapshot(player).coins());
        Ledger ledger = ledger();
        ledger.credit(player, 37);
        assertEquals(287, ledger().snapshot(player).coins());
        assertEquals(250, ledger().snapshot(UUID.randomUUID()).coins());
        assertEquals(250, new Ledger(directory.resolve("another-server"), clock).snapshot(player).coins());
    }

    @Test void purchaseDebitsAndDeliversOnlyAfterDelay() throws IOException {
        Ledger ledger = ledger();
        Ledger.Parcel parcel = ledger.buy(player, "minecraft:diamond", 1, 200);
        assertEquals(50, ledger.snapshot(player).coins());
        assertEquals(1, ledger.snapshot(player).parcels().size());
        assertThrows(IllegalArgumentException.class, () -> ledger.collect(player, parcel.id()));
        clock.millis += Ledger.DELIVERY_MS - 1;
        assertThrows(IllegalArgumentException.class, () -> ledger.collect(player, parcel.id()));
        clock.millis++;
        assertEquals(parcel, ledger.collect(player, parcel.id()));
        assertTrue(ledger.snapshot(player).parcels().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ledger.collect(player, parcel.id()));
    }

    @Test void insufficientFundsNeverChangesAccount() throws IOException {
        Ledger ledger = ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "minecraft:diamond", 2, 400));
        assertEquals(250, ledger.snapshot(player).coins());
        assertTrue(ledger.snapshot(player).parcels().isEmpty());
    }

    @Test void rejectsInvalidPurchaseAmountsAndIds() throws IOException {
        Ledger ledger = ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "minecraft:diamond", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "minecraft:diamond", 4097, 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "minecraft:diamond", 1, -100));
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "../oops", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "minecraft:diamond", 1, Long.MAX_VALUE));
        assertEquals(250, ledger.snapshot(player).coins());
    }

    @Test void dailyBonusSurvivesRestartAndRequiresFull24Hours() throws IOException {
        Ledger ledger = ledger();
        ledger.daily(player);
        assertEquals(350, ledger.snapshot(player).coins());
        assertThrows(IllegalArgumentException.class, () -> ledger().daily(player));
        clock.millis += Ledger.DAILY_MS - 1;
        assertThrows(IllegalArgumentException.class, () -> ledger().daily(player));
        clock.millis++;
        Ledger reopened = ledger();
        reopened.daily(player);
        assertEquals(450, reopened.snapshot(player).coins());
    }

    @Test void parcelOwnershipCannotBeBypassed() throws IOException {
        Ledger ledger = ledger();
        Ledger.Parcel parcel = ledger.buy(player, "minecraft:bread", 16, 80);
        clock.millis += Ledger.DELIVERY_MS;
        assertThrows(IllegalArgumentException.class, () -> ledger.collect(UUID.randomUUID(), parcel.id()));
        assertEquals(parcel, ledger.readyParcel(player, parcel.id()));
    }

    @Test void ordersPersistAcrossRestartsAndOfflineTimeCounts() throws IOException {
        Ledger.Parcel parcel = ledger().buy(player, "minecraft:bread", 16, 80);
        clock.millis += Ledger.DELIVERY_MS;
        Ledger reopened = ledger();
        assertEquals(170, reopened.snapshot(player).coins());
        assertEquals(parcel, reopened.collect(player, parcel.id()));
        assertTrue(ledger().snapshot(player).parcels().isEmpty());
    }

    @Test void fullMailboxRejectsWithoutCharging() throws IOException {
        Ledger ledger = ledger();
        for (int i = 0; i < Ledger.MAX_ORDERS; i++) ledger.buy(player, "minecraft:torch", 1, 1);
        long before = ledger.snapshot(player).coins();
        assertThrows(IllegalArgumentException.class, () -> ledger.buy(player, "minecraft:torch", 1, 1));
        assertEquals(before, ledger.snapshot(player).coins());
        assertEquals(Ledger.MAX_ORDERS, ledger.snapshot(player).parcels().size());
    }

    @Test void corruptedFilesArePreservedAndNotReset() throws IOException {
        Path file = directory.resolve(player + ".json");
        Files.writeString(file, "not valid json");
        assertThrows(IOException.class, () -> ledger().snapshot(player));
        assertEquals("not valid json", Files.readString(file));
        Files.writeString(file, "{\"schema\":1,\"coins\":-1,\"nextBonus\":0,\"parcels\":[]}");
        assertThrows(IOException.class, () -> ledger().snapshot(player));
        Files.writeString(file, "{}");
        assertThrows(IOException.class, () -> ledger().snapshot(player));
    }

    @Test void failedDiskWriteRollsBackInMemory() throws IOException {
        Ledger ledger = ledger();
        ledger.snapshot(player);
        Path destination = directory.resolve(player + ".json");
        Files.delete(destination);
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("blocker"), "keep");
        assertThrows(IOException.class, () -> ledger.buy(player, "minecraft:bread", 16, 80));
        assertEquals(250, ledger.snapshot(player).coins());
        assertTrue(ledger.snapshot(player).parcels().isEmpty());
    }

    @Test void failedParcelRemovalKeepsParcel() throws IOException {
        Ledger ledger = ledger();
        Ledger.Parcel parcel = ledger.buy(player, "minecraft:bread", 16, 80);
        clock.millis += Ledger.DELIVERY_MS;
        Path destination = directory.resolve(player + ".json");
        Files.delete(destination);
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("blocker"), "keep");
        assertThrows(IOException.class, () -> ledger.collect(player, parcel.id()));
        assertEquals(parcel, ledger.readyParcel(player, parcel.id()));
    }

    @Test void snapshotsAreImmutable() throws IOException {
        Ledger ledger = ledger();
        assertThrows(UnsupportedOperationException.class, () -> ledger.snapshot(player).parcels().clear());
    }

    @Test void balanceCannotOverflow() throws IOException {
        Ledger ledger = ledger();
        ledger.credit(player, Ledger.MAX_COINS - Ledger.START_COINS);
        assertThrows(IllegalArgumentException.class, () -> ledger.credit(player, 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.credit(player, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ledger.credit(player, -1));
        assertThrows(IllegalArgumentException.class, () -> ledger.daily(player));
        assertEquals(Ledger.MAX_COINS, ledger.snapshot(player).coins());
    }
}
