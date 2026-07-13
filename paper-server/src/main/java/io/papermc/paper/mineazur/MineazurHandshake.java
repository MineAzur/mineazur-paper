package io.papermc.paper.mineazur;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * MineAzur — handshake mod client → serveur (gate serveur, addendum Phase 1). Le mod envoie, en phase de
 * configuration, un payload SERVERBOUND sur {@code mineazur:hello} portant sa version ; le serveur le décode ici
 * et décide (via {@link MineazurConfig}) d'autoriser ou de refouler. Sans ce payload = client vanilla → refus.
 *
 * <p>Format (v1), reçu comme {@code DiscardedPayload(id, byte[])} (tout payload serverbound l'est) :
 * <pre>int magic = 0x4D5A4831 ("MZH1") ; varint protocol ; utf modVersion</pre>
 */
public final class MineazurHandshake {

    public static final Identifier HELLO_CHANNEL = Identifier.fromNamespaceAndPath("mineazur", "hello");
    public static final int PROTOCOL = 1;
    private static final int MAGIC = 0x4D5A4831;

    private MineazurHandshake() {
    }

    public enum Status { OK, MALFORMED, BAD_VERSION }

    public record Result(Status status, String modVersion) {}

    /** Décode + valide les octets d'un {@code mineazur:hello}. */
    public static Result check(final byte[] data) {
        try {
            final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            if (buf.readInt() != MAGIC) {
                return new Result(Status.MALFORMED, "");
            }
            final int protocol = buf.readVarInt();
            final String modVersion = buf.readUtf();
            if (protocol != PROTOCOL) {
                return new Result(Status.BAD_VERSION, modVersion);
            }
            final MineazurConfig cfg = MineazurConfig.get();
            if (cfg.enforceVersion() && !cfg.requiredVersion().equals(modVersion)) {
                return new Result(Status.BAD_VERSION, modVersion);
            }
            return new Result(Status.OK, modVersion);
        } catch (final RuntimeException e) {
            return new Result(Status.MALFORMED, "");
        }
    }
}
