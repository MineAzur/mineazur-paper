package io.papermc.paper.mineazur;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * MineAzur — configuration du <b>gate serveur</b> (addendum Phase 1), lue dans un fichier DÉDIÉ
 * {@code mineazur.yml} à la RACINE du serveur (répertoire de travail). Contrôle si le serveur exige le mod
 * client MineAzur (et sa version) avant d'autoriser l'entrée en jeu.
 *
 * <p>Le serveur (protocole vanilla) ne voit pas la liste de mods NeoForge : le mod envoie un payload
 * {@code mineazur:hello} (version) en phase de configuration ; le serveur l'exige (sinon = client vanilla) et
 * refuse proprement, AVANT le premier chunk (qui ferait crasher un client vanilla sur les IDs custom).
 *
 * <p>Singleton chargé paresseusement. Écrit un fichier par défaut commenté au 1er démarrage.
 */
public final class MineazurConfig {

    private static volatile MineazurConfig instance;

    private final boolean requireMod;
    private final boolean enforceVersion;
    private final String requiredVersion;
    private final int timeoutTicks;
    private final String kickNoMod;
    private final String kickBadVersion;

    private MineazurConfig(final boolean requireMod, final boolean enforceVersion, final String requiredVersion,
                          final int timeoutTicks, final String kickNoMod, final String kickBadVersion) {
        this.requireMod = requireMod;
        this.enforceVersion = enforceVersion;
        this.requiredVersion = requiredVersion;
        this.timeoutTicks = timeoutTicks;
        this.kickNoMod = kickNoMod;
        this.kickBadVersion = kickBadVersion;
    }

    public static MineazurConfig get() {
        MineazurConfig i = instance;
        if (i == null) {
            synchronized (MineazurConfig.class) {
                if (instance == null) {
                    instance = load();
                }
                i = instance;
            }
        }
        return i;
    }

    private static final String DEFAULT_YML = """
        # mineazur.yml — gate serveur MineAzur (impose le mod client avant d'autoriser la connexion).
        #
        # Le serveur (protocole vanilla) ne voit pas les mods NeoForge : le mod envoie un payload
        # 'mineazur:hello' (version) en phase de configuration. Le serveur l'exige et refuse proprement les
        # clients sans mod (= vanilla) ou de mauvaise version, AVANT le 1er chunk (qui ferait crasher un vanilla
        # sur les IDs de blocs custom). Coupe le serveur pour recharger ce fichier.

        # Exiger le mod client MineAzur. false = accepter tout client (dev/vanilla ; les blocs custom crashent).
        require-mod: true

        # Version du mod attendue (le mod envoie sa version dans 'hello').
        required-mod-version: "0.6.18"

        # Vérifier la version exacte. false = mod requis mais n'importe quelle version acceptée.
        enforce-version: true

        # Délai (secondes) pour recevoir 'hello' en config avant de couper (garde anti-blocage).
        handshake-timeout-seconds: 10

        # Messages de refus (%expected% = version attendue).
        kick-no-mod: "Ce serveur nécessite le mod MineAzur. Utilise le launcher MineAzur pour rejoindre."
        kick-bad-version: "Version du mod MineAzur incompatible (attendu %expected%). Mets à jour via le launcher MineAzur."
        """;

    private static MineazurConfig load() {
        final File f = new File("mineazur.yml");
        if (!f.isFile()) {
            try {
                Files.writeString(f.toPath(), DEFAULT_YML, StandardCharsets.UTF_8);
            } catch (final IOException ignored) {
                // on continuera sur les valeurs par défaut en mémoire
            }
        }
        final YamlConfiguration c = f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        return new MineazurConfig(
                c.getBoolean("require-mod", true),
                c.getBoolean("enforce-version", true),
                c.getString("required-mod-version", "0.6.18"),
                Math.max(20, c.getInt("handshake-timeout-seconds", 10) * 20),
                c.getString("kick-no-mod",
                        "Ce serveur nécessite le mod MineAzur. Utilise le launcher MineAzur pour rejoindre."),
                c.getString("kick-bad-version",
                        "Version du mod MineAzur incompatible (attendu %expected%). Mets à jour via le launcher MineAzur."));
    }

    public boolean requireMod() {
        return this.requireMod;
    }

    public boolean enforceVersion() {
        return this.enforceVersion;
    }

    public String requiredVersion() {
        return this.requiredVersion;
    }

    public int timeoutTicks() {
        return this.timeoutTicks;
    }

    public String kickNoMod() {
        return this.kickNoMod;
    }

    /** Message de mauvaise version, {@code %expected%} substitué. */
    public String kickBadVersion() {
        return this.kickBadVersion.replace("%expected%", this.requiredVersion);
    }
}
