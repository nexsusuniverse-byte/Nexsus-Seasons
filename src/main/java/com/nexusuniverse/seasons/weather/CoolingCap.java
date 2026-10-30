package com.nexusuniverse.seasons.weather;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * A single custom-tagged Leather Helmet that makes summer heat exhaustion (HeatExhaustionManager)
 * completely ignore the wearer -- no heat buildup at all while worn, mirroring exactly how
 * WindproofBoots grounds a player against wind. Take it off and heat starts accumulating again on
 * the very next check; nothing is remembered between wears, so there's no separate "cooldown"
 * state to manage.
 *
 * Leather armor specifically (not netherite, unlike the boots) so it can be dyed a light color
 * that actually reads as "built to reflect the sun" -- default here is a pale straw/cream.
 *
 * Tagged via PersistentDataContainer rather than matching on name/lore, same as WindproofBoots, so
 * it survives renaming in an anvil, resource packs, etc, and can't be spoofed by a player crafting
 * or naming a lookalike leather cap themselves.
 */
public final class CoolingCap {

    private final NamespacedKey key;

    public CoolingCap(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "cooling_cap");
    }

    /**
     * The exact PDC key this class checks/sets - exposed so an admin who'd rather hand these out
     * with the vanilla /give command than /nexusseasons coolingcap give can do so directly. In
     * 1.20.5+'s component format, a PersistentDataContainer boolean/byte entry IS the item's
     * minecraft:custom_data component, so the equivalent vanilla command is:
     *
     *   /give &lt;player&gt; leather_helmet[custom_data={"{namespace}:{key}":1b}]
     *
     * with {namespace} and {key} filled in from this method - e.g. for the default plugin name
     * this is nexusseasons:cooling_cap. Such an item won't have the name/lore/dye color create()
     * below sets, only the tag that actually matters for the heat check.
     */
    public NamespacedKey key() {
        return key;
    }

    /** True if the player currently has a cooling-cap-tagged item in their helmet slot. */
    public boolean isWorn(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) return false;
        ItemMeta meta = helmet.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Builds a fresh, correctly-tagged cap, ready to give to a player. */
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(0xFF, 0xF3, 0xC4)); // pale straw/cream -- reads as sun-reflective, not just "dyed"
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Cooling Cap");
        meta.setLore(List.of(
                ChatColor.GRAY + "Keeps summer heat from ever reaching you.",
                ChatColor.GRAY + "No heat buildup at all while worn.",
                ChatColor.GRAY + "Take it off and the heat comes right back."
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
