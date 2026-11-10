package com.nexusuniverse.seasons.weather;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * A single custom-tagged pair of Netherite Boots that makes the ambient wind (WindManager)
 * completely ignore the wearer - no horizontal push at all, whether they're walking/jumping on
 * the ground or gliding on an elytra. Take them off and the wind resumes on the very next tick;
 * nothing is remembered between wears, so there's no separate "cooldown" state to manage.
 *
 * Deliberately does NOT touch firework-rocket elytra boosting - that's a vanilla mechanic
 * completely unrelated to WindManager's push, so it already keeps working normally regardless of
 * whether these boots are worn. What the boots remove is only the ambient wind's own push, which
 * is the thing that currently also assists/hinders elytra flight - see WindManager#pushPlayers.
 *
 * Tagged via PersistentDataContainer rather than matching on name/lore, so it survives renaming
 * in an anvil, resource packs, etc, and can't be spoofed by a player crafting or naming a
 * lookalike pair of boots themselves.
 */
public final class WindproofBoots {

    private final NamespacedKey key;

    public WindproofBoots(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "windproof_boots");
    }

    /**
     * The exact PDC key this class checks/sets - exposed so an admin who'd rather hand these out
     * with the vanilla /give command than /nexusseasons windboots give can do so directly. In
     * 1.20.5+'s component format, a PersistentDataContainer boolean/byte entry IS the item's
     * minecraft:custom_data component, so the equivalent vanilla command is:
     *
     *   /give &lt;player&gt; netherite_boots[custom_data={"{namespace}:{key}":1b}]
     *
     * with {namespace} and {key} filled in from this method - e.g. for the default plugin name
     * this is nexusseasons:windproof_boots. Such an item won't have the name/lore create() below
     * sets, only the tag that actually matters for the wind check.
     */
    public NamespacedKey key() {
        return key;
    }

    /** True if the player currently has a windproof-tagged item in their boots slot. */
    public boolean isWorn(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null) return false;
        ItemMeta meta = boots.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Builds a fresh, correctly-tagged pair, ready to give to a player. */
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Stormward Boots");
        meta.setLore(List.of(
                ChatColor.GRAY + "Grounds you completely against wind.",
                ChatColor.GRAY + "No push while worn - on foot or gliding.",
                ChatColor.GRAY + "Take them off and the wind comes right back."
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
