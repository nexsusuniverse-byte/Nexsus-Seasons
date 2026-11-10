package com.nexusuniverse.seasons.weather;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

/**
 * Registers a real crafting-table recipe for the Cooling Cap, so it's obtainable through normal
 * survival play instead of only via /nexusseasons coolingcap give -- same "give-command item
 * graduates to a real recipe" step NexusSurvival already took for its Water Purification and
 * Disease Cure items (see that plugin's WaterPurificationRecipe/DiseaseCureRecipes for the
 * precedent this mirrors).
 *
 * Shape matches vanilla's own Leather Helmet footprint exactly (2 rows, 3 columns, middle of the
 * bottom row left open for the head):
 *
 *   H L H
 *   L   L
 *
 * with the two top corners swapped from Leather to Hay Bale -- reads as a wide straw brim woven
 * onto a leather cap, which is exactly what CoolingCap#create()'s pale straw/cream dye + lore
 * already describe. The crafted result comes straight from CoolingCap#create(), so it's tagged,
 * colored, and named identically to a /give'd one -- there's no separate "recipe-only" variant to
 * keep in sync.
 */
public final class CoolingCapRecipe {

    private CoolingCapRecipe() {}

    public static void register(Plugin plugin, CoolingCap coolingCap) {
        NamespacedKey key = new NamespacedKey(plugin, "cooling_cap");
        ItemStack result = coolingCap.create();

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("HLH", "L L");
        recipe.setIngredient('H', new RecipeChoice.MaterialChoice(Material.HAY_BLOCK));
        recipe.setIngredient('L', new RecipeChoice.MaterialChoice(Material.LEATHER));

        Bukkit.addRecipe(recipe);
    }
}
