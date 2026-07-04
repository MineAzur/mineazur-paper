package io.papermc.paper.mineazur;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MineAzur — registry-sync serveur→client (addendum Phase 1, FONDATION).
 *
 * <p>Le serveur (fork Paper, protocole VANILLA) ne fait pas le handshake modded de NeoForge : il émet donc,
 * en PHASE DE CONFIGURATION (avant tout paquet de chunk), un custom payload sur le canal
 * {@code mineazur:registry_sync} portant les IDs entiers réseau que le serveur a figés pour nos objets custom
 * (registres BLOCK et ITEM + l'IdMapper des block states). Le client compare (garde Niveau 1) ou, à terme,
 * réconcilie (Niveau 2).
 *
 * <p>Transport : {@link DiscardedPayload} — le codec custom-payload de la phase config a pour fallback
 * {@code DiscardedPayload.codec(id, 1 MiB)}, donc un canal inconnu du protocole vanilla passe en octets bruts
 * (exactement comme un plugin-message modded traversant un serveur vanilla). Aucun type à enregistrer.
 *
 * <p>Format (v1) — que des VarInt/Utf, ordre déterministe (itération du registre = ordre d'enregistrement) :
 * <pre>
 *   int   magic      = 0x4D5A5231 ("MZR1")
 *   varint blockRegistrySize   (BuiltInRegistries.BLOCK.size())
 *   varint itemRegistrySize    (BuiltInRegistries.ITEM.size())
 *   varint blockStateTotal     (Block.BLOCK_STATE_REGISTRY.size())      // totaux = détecteur de drift
 *   varint customBlockCount
 *     [ utf blockKey ; varint blockId ; varint stateCount ; stateCount × varint stateId ]   // ordre getPossibleStates()
 *   varint customItemCount
 *     [ utf itemKey ; varint itemId ]
 * </pre>
 */
public final class MineazurRegistrySync {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("mineazur", "registry_sync");
    private static final int MAGIC = 0x4D5A5231;
    private static final String NS = "mineazur";

    private MineazurRegistrySync() {
    }

    public static ClientboundCustomPayloadPacket buildPacket() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(MAGIC);
        buf.writeVarInt(BuiltInRegistries.BLOCK.size());
        buf.writeVarInt(BuiltInRegistries.ITEM.size());
        buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.size());

        // Blocs custom (namespace mineazur), dans l'ordre d'enregistrement.
        final FriendlyByteBuf blocksBuf = new FriendlyByteBuf(Unpooled.buffer());
        int customBlocks = 0;
        for (final Block block : BuiltInRegistries.BLOCK) {
            final Identifier key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null || !NS.equals(key.getNamespace())) {
                continue;
            }
            customBlocks++;
            blocksBuf.writeUtf(key.toString());
            blocksBuf.writeVarInt(BuiltInRegistries.BLOCK.getId(block));
            final var states = block.getStateDefinition().getPossibleStates();
            blocksBuf.writeVarInt(states.size());
            for (final BlockState state : states) {
                blocksBuf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(state));
            }
        }
        buf.writeVarInt(customBlocks);
        buf.writeBytes(blocksBuf);

        // Items custom.
        final FriendlyByteBuf itemsBuf = new FriendlyByteBuf(Unpooled.buffer());
        int customItems = 0;
        for (final Item item : BuiltInRegistries.ITEM) {
            final Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null || !NS.equals(key.getNamespace())) {
                continue;
            }
            customItems++;
            itemsBuf.writeUtf(key.toString());
            itemsBuf.writeVarInt(BuiltInRegistries.ITEM.getId(item));
        }
        buf.writeVarInt(customItems);
        buf.writeBytes(itemsBuf);

        final byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new ClientboundCustomPayloadPacket(new DiscardedPayload(CHANNEL, data));
    }
}
