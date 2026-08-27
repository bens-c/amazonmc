package de.amazonmc;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;

public final class Catalog {
    public enum Category { ALL, BUILD, MATERIAL, FOOD, GEAR }
    public record Product(String name, Item item, int amount, int price, int sellEach, Category category) {
        public String itemId() { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    }
    public static final List<Product> ALL = List.of(
        new Product("Eichenstämme", Items.OAK_LOG, 16, 64, 2, Category.BUILD),
        new Product("Bruchstein", Items.COBBLESTONE, 64, 64, 0, Category.BUILD),
        new Product("Glas", Items.GLASS, 32, 64, 1, Category.BUILD),
        new Product("Steinziegel", Items.STONE_BRICKS, 32, 64, 1, Category.BUILD),
        new Product("Weiße Wolle", Items.WHITE_WOOL, 16, 64, 2, Category.BUILD),
        new Product("Laternen", Items.LANTERN, 8, 80, 4, Category.BUILD),
        new Product("Fackeln", Items.TORCH, 32, 32, 0, Category.BUILD),
        new Product("Eisenbarren", Items.IRON_INGOT, 8, 160, 8, Category.MATERIAL),
        new Product("Goldbarren", Items.GOLD_INGOT, 8, 240, 12, Category.MATERIAL),
        new Product("Diamant", Items.DIAMOND, 1, 200, 80, Category.MATERIAL),
        new Product("Redstone", Items.REDSTONE, 16, 64, 1, Category.MATERIAL),
        new Product("Lapislazuli", Items.LAPIS_LAZULI, 16, 64, 1, Category.MATERIAL),
        new Product("Kohle", Items.COAL, 16, 64, 2, Category.MATERIAL),
        new Product("Enderperlen", Items.ENDER_PEARL, 4, 200, 20, Category.MATERIAL),
        new Product("Brot", Items.BREAD, 16, 80, 2, Category.FOOD),
        new Product("Steak", Items.COOKED_BEEF, 16, 128, 3, Category.FOOD),
        new Product("Goldene Karotten", Items.GOLDEN_CARROT, 8, 160, 8, Category.FOOD),
        new Product("Äpfel", Items.APPLE, 16, 64, 1, Category.FOOD),
        new Product("Ofenkartoffeln", Items.BAKED_POTATO, 16, 64, 1, Category.FOOD),
        new Product("Goldener Apfel", Items.GOLDEN_APPLE, 1, 240, 80, Category.FOOD),
        new Product("Kürbiskuchen", Items.PUMPKIN_PIE, 8, 80, 4, Category.FOOD),
        new Product("Eisenspitzhacke", Items.IRON_PICKAXE, 1, 160, 0, Category.GEAR),
        new Product("Diamantspitzhacke", Items.DIAMOND_PICKAXE, 1, 800, 0, Category.GEAR),
        new Product("Eisenschwert", Items.IRON_SWORD, 1, 120, 0, Category.GEAR),
        new Product("Schild", Items.SHIELD, 1, 100, 0, Category.GEAR),
        new Product("Bogen", Items.BOW, 1, 100, 0, Category.GEAR),
        new Product("Pfeile", Items.ARROW, 32, 64, 1, Category.GEAR),
        new Product("Wassereimer", Items.WATER_BUCKET, 1, 100, 0, Category.GEAR)
    );
    public static Product byItem(Item item) {
        return ALL.stream().filter(p -> p.item == item).findFirst().orElse(null);
    }
    private Catalog() { }
}
