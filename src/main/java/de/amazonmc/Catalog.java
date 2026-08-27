package de.amazonmc;

import org.bukkit.Material;

import java.util.List;

public final class Catalog {
    public enum Category { ALL, BUILD, MATERIAL, FOOD, GEAR }
    public record Product(String name, Material item, int amount, int price, int sellEach, Category category) {
        public String itemId() { return item.getKey().toString(); }
    }
    public static final List<Product> ALL = List.of(
        new Product("Eichenstämme", Material.OAK_LOG, 16, 64, 2, Category.BUILD),
        new Product("Bruchstein", Material.COBBLESTONE, 64, 64, 0, Category.BUILD),
        new Product("Glas", Material.GLASS, 32, 64, 1, Category.BUILD),
        new Product("Steinziegel", Material.STONE_BRICKS, 32, 64, 1, Category.BUILD),
        new Product("Weiße Wolle", Material.WHITE_WOOL, 16, 64, 2, Category.BUILD),
        new Product("Laternen", Material.LANTERN, 8, 80, 4, Category.BUILD),
        new Product("Fackeln", Material.TORCH, 32, 32, 0, Category.BUILD),
        new Product("Eisenbarren", Material.IRON_INGOT, 8, 160, 8, Category.MATERIAL),
        new Product("Goldbarren", Material.GOLD_INGOT, 8, 240, 12, Category.MATERIAL),
        new Product("Diamant", Material.DIAMOND, 1, 200, 80, Category.MATERIAL),
        new Product("Redstone", Material.REDSTONE, 16, 64, 1, Category.MATERIAL),
        new Product("Lapislazuli", Material.LAPIS_LAZULI, 16, 64, 1, Category.MATERIAL),
        new Product("Kohle", Material.COAL, 16, 64, 2, Category.MATERIAL),
        new Product("Enderperlen", Material.ENDER_PEARL, 4, 200, 20, Category.MATERIAL),
        new Product("Brot", Material.BREAD, 16, 80, 2, Category.FOOD),
        new Product("Steak", Material.COOKED_BEEF, 16, 128, 3, Category.FOOD),
        new Product("Goldene Karotten", Material.GOLDEN_CARROT, 8, 160, 8, Category.FOOD),
        new Product("Äpfel", Material.APPLE, 16, 64, 1, Category.FOOD),
        new Product("Ofenkartoffeln", Material.BAKED_POTATO, 16, 64, 1, Category.FOOD),
        new Product("Goldener Apfel", Material.GOLDEN_APPLE, 1, 240, 80, Category.FOOD),
        new Product("Kürbiskuchen", Material.PUMPKIN_PIE, 8, 80, 4, Category.FOOD),
        new Product("Eisenspitzhacke", Material.IRON_PICKAXE, 1, 160, 0, Category.GEAR),
        new Product("Diamantspitzhacke", Material.DIAMOND_PICKAXE, 1, 800, 0, Category.GEAR),
        new Product("Eisenschwert", Material.IRON_SWORD, 1, 120, 0, Category.GEAR),
        new Product("Schild", Material.SHIELD, 1, 100, 0, Category.GEAR),
        new Product("Bogen", Material.BOW, 1, 100, 0, Category.GEAR),
        new Product("Pfeile", Material.ARROW, 32, 64, 1, Category.GEAR),
        new Product("Wassereimer", Material.WATER_BUCKET, 1, 100, 0, Category.GEAR)
    );
    public static Product byItem(Material item) {
        return ALL.stream().filter(p -> p.item == item).findFirst().orElse(null);
    }
    private Catalog() { }
}
