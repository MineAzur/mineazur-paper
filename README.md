# mineazur-paper — le serveur MineAzur (fork Paper patché)

Fork de [Paper](https://github.com/PaperMC/Paper) **26.1.2** patché pour **MineAzur**, la résurrection d'un
serveur Minecraft custom français de 2011 (CraftBukkit 1.2.5) sur version moderne.

**Ce que ce fork ajoute :** les **29 blocs custom** et **27 items** de MineAzur, enregistrés comme de **vraies
entrées du registre vanilla** (`BuiltInRegistries.BLOCK`), exactement comme le plugin `SBTBlocks` écrasait
`Block.byId[]` en 2011 — mais proprement, dans le registre moderne.

> **Branche de travail : `mineazur`** (pas `main`). `main` suit l'upstream.

## Pourquoi un fork, et pas un plugin ?

Parce qu'on veut des blocs **réels**, pas des imitations :

- **Nova / ItemsAdder** (backing-states, note-block, resource-pack) → écartés : ils supposent un **client
  vanilla** et stockent le custom **hors palette**, ce qui aurait compliqué la migration de la map 2011.
- **Paper vanilla + note-block** → écarté pour la même raison.

Retenu : **serveur patché + client moddé** ([`mineazur-client`](https://github.com/MineAzur/mineazur-client),
NeoForge) qui enregistrent le **même** contenu, dans le **même ordre**. Le *pourquoi* complet :
[`reference/architecture/PATCH_STRATEGY.md`](https://github.com/MineAzur/mineazur-docs/blob/main/reference/architecture/PATCH_STRATEGY.md).

## ⚠️ Le piège central (à ne jamais oublier)

Le registre `BuiltInRegistries.BLOCK` est **statique et n'est PAS transmis sur le réseau**. Ce qui circule, ce
sont les **IDs d'état de palette**, attribués par **ordre d'enregistrement**. Serveur et client doivent donc
enregistrer **les mêmes blocs, dans le même ordre, avec le même ordre de propriétés** — sinon le joueur voit
des blocs faux, ou se fait déconnecter.

D'où trois garde-fous :
1. **Source unique** — [`shared/`](https://github.com/MineAzur/mineazur-shared) (`mineazur_blocks.json` /
   `mineazur_items.json`), **copié** dans `paper-server/src/main/resources/` **et** dans le mod client.
   L'ordre du fichier **EST** le contrat. **Toujours ajouter EN FIN**, jamais au milieu.
2. **Canal `mineazur:registry_sync`** — le serveur annonce ses IDs au client en phase de configuration.
3. **Garde à échec-fort** côté client — divergence = déconnexion explicite plutôt que corruption silencieuse.

Invariants **I1–I8** :
[`reference/architecture/REGISTRY_ALIGNMENT.md`](https://github.com/MineAzur/mineazur-docs/blob/main/reference/architecture/REGISTRY_ALIGNMENT.md).

## Prérequis

- **JDK 25** (imposé par 26.1.2) → `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk`
- Un accès réseau au premier build (Paperclip télécharge le `server.jar` de Mojang).

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk

./gradlew applyPatches                 # matérialise paper-server/src/minecraft (généré, GITIGNORÉ)
./gradlew :paper-server:compileJava    # compile (feedback rapide)
./gradlew runServer                    # serveur de test (dossier run/, déjà configuré)
./gradlew createPaperclipJar           # jar distribuable -> paper-server/build/libs/paper-paperclip-*.jar
```

> `createPaperclipJar` — **pas** `createMojmapPaperclipJar`, qui n'existe pas.
> Le paperclip produit est au **format papermc.io** : autonome, `java -jar`. Il **télécharge le server.jar de
> Mojang au 1er démarrage** (sa redistribution étant interdite) — c'est voulu, pas un défaut.

## Où va le code

| Emplacement | Quoi | Versionné ? |
|---|---|---|
| `paper-server/src/minecraft/java/` | **sources vanilla remappées** (noms Mojang) — nos blocs custom (`ChairBlock`, `SangBlock`, `TombeBlock`…) + hooks dans `Blocks.java`/`Items.java` | ❌ **gitignoré** → capturé en **patches** |
| `paper-server/patches/{sources,features,resources}/` | les patches (943 / 32 / 6) = **la vraie source de vérité** | ✅ |
| `paper-server/src/main/java/io/papermc/paper/mineazur/` | notre code **propre** (générateur, registry-sync, gate, config) — pas du patch | ✅ |
| `paper-server/src/main/resources/mineazur_*.json` | **copie** de `shared/` (à resynchroniser à la main) | ✅ |
| `run/` | serveur de test (mondes, plugins, configs) | ❌ gitignoré |

## ⚠️ Figer ses modifications (le piège n°1)

`src/minecraft` est **gitignoré**. Éditer un fichier là-dedans puis faire `git commit` ne capture **rien** : il
faut convertir les édits en patches.

```bash
./gradlew fixupSourcePatches      # 1) intègre les édits au commit de patches
./gradlew rebuildSourcePatches    # 2) régénère les .patch  <-- INVOCATION SÉPARÉE
git add paper-server/patches && git commit
```

> 🔥 **Deux invocations Gradle DISTINCTES.** Enchaînées dans la même (`fixupSourcePatches rebuildPatches`), le
> rebuild peut échouer sur un `git stash` interne et **supprimer les patches** (déjà vécu : 939 patches perdus,
> récupérés par `git checkout -- paper-server/patches/sources/`). Vérifier le compte de patches après coup.

## Ce qui est patché

Inventaire complet, point par point, avec l'invariant que chacun maintient :
[`reference/blocs/PATCH_INVENTORY.md`](https://github.com/MineAzur/mineazur-docs/blob/main/reference/blocs/PATCH_INVENTORY.md).
En résumé :

- **Hooks** `static{}` dans `Blocks.java` / `Items.java` → appellent notre générateur **avant** que
  `BLOCK_STATE_REGISTRY` soit peuplé (**I2** — le plus fragile au rebase).
- **Classes d'archétype** : `ChairBlock`, `PlafondBlock`, `MineazurHorizontalBlock`, `MineazurWoolStairsBlock`,
  `SangBlock` (bloc connecté, blob 47 tuiles), `DiamondLampBlock`, `CoinDeToitBlock`, `TombeBlock`. Chacune a
  un **miroir homonyme** côté client dont l'ordre des propriétés doit être identique (**I3**).
- **Émission de `registry_sync`** dans `ServerConfigurationPacketListenerImpl` (**I4**).
- **Fallbacks « mur enum Bukkit »** : nos `mineazur:*` n'ont pas d'entrée dans les enums `Material`/`EntityType`
  (figés) → `CraftMagicNumbers`/`CraftEntityType` mappent sur un proche vanilla, sinon NPE au clic.

## Serveur de test

`run/` est pré-configuré (map 2011 migrée, plugins maison, grades). Pour le reconstruire de zéro depuis les
sauvegardes de 2011 : `docs/scripts/migration/fresh_install.sh` (⚠️ **wipe `run/`**).

```bash
./gradlew runServer          # ne s'arrête PAS sur EOF stdin -> piloter par un FIFO, envoyer "stop"
./gradlew --stop             # tuer le daemon si un serveur reste accroché au port 25565
```

> Ne jamais `kill -9` à l'aveugle : le daemon Gradle peut relancer la tâche.

## Monter de version (quand MC bumpe)

Procédure orientée invariants, pas commandes figées : skill **`mineazur-upgrade-server`**
([`skills/`](https://github.com/MineAzur/mineazur-docs/tree/main/skills)). L'idée : rebase paperweight →
re-localiser les hooks (I2/I4) → recompiler les classes d'archétype contre le nouveau mojmap (**en lisant les
sources cibles, jamais de mémoire** — I8) → la **garde client** sert de test d'acceptation (I6).

## Documentation

Tout vit dans **[`mineazur-docs`](https://github.com/MineAzur/mineazur-docs)** — point d'entrée : `INDEX.md`.
La doc y est rangée par **statut de vérité** (`reference/` = autorité · `history/` = archive · `_archive/` =
périmé).

---

## Upstream & licence

Ce dépôt est un **fork de [PaperMC/Paper](https://github.com/PaperMC/Paper)** — tout le travail de fond est le
leur ; nous n'ajoutons qu'une couche de contenu custom. Paper reste sous **[GPL v3](LICENSE.md)** (héritée de
Spigot/Bukkit/CraftBukkit), et ce fork aussi.

- Upstream : `git remote add upstream https://github.com/PaperMC/Paper.git` (déjà configuré)
- Doc Paper : [docs.papermc.io](https://docs.papermc.io) · API : [`paper-api`](paper-api) ·
  [Contribuer à Paper](CONTRIBUTING.md) (⚠️ nos patches MineAzur n'ont **rien** à y faire)
- Soutenir PaperMC : [papermc.io/sponsors](https://papermc.io/sponsors)

> 📄 **Ce README a été réécrit pour MineAzur** ; l'original de PaperMC est
> [ici](https://github.com/PaperMC/Paper/blob/main/README.md). Attendre un **conflit sur ce fichier** lors d'un
> rebase upstream : garder notre version (`git checkout --ours README.md`).
