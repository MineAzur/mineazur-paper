package io.papermc.paper.mineazur;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * MineAzur — comportements custom qui ne relèvent pas du registre (portage de {@code tboss.SBTBlocks}).
 * L'« assurance » (portage de {@code SBTBlocksListener.onPlayerDeath}/{@code Death}) : si le joueur meurt en
 * possédant au moins une assurance, il en consomme une et conserve inventaire + niveau + XP (aucun drop).
 * Câblé dans {@code ServerPlayer.die} (l'API moderne {@code PlayerDeathEvent}/keepLevel rend inutile le
 * snapshot manuel + handler de respawn de 2011).
 */
public final class MineazurBehaviors {
    private static final Identifier ASSURANCE_ID = Identifier.fromNamespaceAndPath("mineazur", "assurance");

    private MineazurBehaviors() {
    }

    private static Item assuranceItem() {
        return BuiltInRegistries.ITEM.getValue(ASSURANCE_ID);
    }

    /** Vrai si le joueur possède au moins une assurance (sans la consommer). */
    public static boolean hasAssurance(final ServerPlayer player) {
        final Item assurance = assuranceItem();
        if (assurance == null) {
            return false;
        }
        for (final ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(assurance)) {
                return true;
            }
        }
        return false;
    }

    /** Consomme UNE assurance et notifie le joueur (message d'origine 2011). À n'appeler qu'une fois la mort confirmée. */
    public static void consumeAssurance(final ServerPlayer player) {
        final Item assurance = assuranceItem();
        if (assurance == null) {
            return;
        }
        final List<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            final ItemStack stack = items.get(i);
            if (!stack.isEmpty() && stack.is(assurance)) {
                stack.shrink(1);
                player.sendSystemMessage(Component.literal("Vous aviez une assurance, votre inventaire vous a été rendu."));
                return;
            }
        }
    }
}
