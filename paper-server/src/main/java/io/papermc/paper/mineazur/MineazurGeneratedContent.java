package io.papermc.paper.mineazur;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChairBlock;
import net.minecraft.world.level.block.MineazurHorizontalBlock;
import net.minecraft.world.level.block.PlafondBlock;
import net.minecraft.world.level.block.MineazurWoolStairsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * MineAzur — générateur runtime des blocs/items custom, piloté par la source unique
 * {@code /mineazur_blocks.json} (identique côté mod NeoForge). L'ordre du fichier = ordre canonique
 * d'enregistrement (fixe les IDs d'états de palette). Appelé depuis {@code Blocks.java}/{@code Items.java}.
 * Archétypes : {@code simple} (dir/non-dir, cube plein), {@code chair} (hitbox 0.15-0.85),
 * {@code plafond} (top-slab), {@code wool_stairs} (StairBlock vanilla sur une laine de base).
 */
public final class MineazurGeneratedContent {
    private static final String NS = "mineazur";
    private static List<Spec> SPECS;

    private record Spec(String name, String archetype, boolean directional, boolean occlusion, int light,
                        float hardness, float resistance, String sound, String mapColor, String base) {}

    private MineazurGeneratedContent() {
    }

    private static float asFloat(final JsonObject p, final String k, final float def) {
        return p.has(k) ? p.get(k).getAsFloat() : def;
    }

    private static String asString(final JsonObject p, final String k, final String def) {
        return p.has(k) ? p.get(k).getAsString() : def;
    }

    private static List<Spec> specs() {
        if (SPECS == null) {
            final List<Spec> list = new ArrayList<>();
            try (InputStream in = MineazurGeneratedContent.class.getResourceAsStream("/mineazur_blocks.json")) {
                if (in == null) {
                    throw new IllegalStateException("mineazur_blocks.json introuvable sur le classpath serveur");
                }
                final JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                final JsonArray blocks = root.getAsJsonArray("blocks");
                for (int i = 0; i < blocks.size(); i++) {
                    final JsonObject b = blocks.get(i).getAsJsonObject();
                    final JsonObject p = b.getAsJsonObject("params");
                    list.add(new Spec(
                        b.get("name").getAsString(),
                        b.get("archetype").getAsString(),
                        p.has("directional") && p.get("directional").getAsBoolean(),
                        !p.has("occlusion") || p.get("occlusion").getAsBoolean(),
                        p.has("light") ? p.get("light").getAsInt() : 0,
                        asFloat(p, "hardness", 1.0F),
                        asFloat(p, "resistance", 1.0F),
                        asString(p, "sound", "STONE"),
                        asString(p, "mapColor", "STONE"),
                        asString(p, "base", null)
                    ));
                }
            } catch (final Exception e) {
                throw new RuntimeException("MineAzur : lecture de mineazur_blocks.json échouée", e);
            }
            SPECS = list;
        }
        return SPECS;
    }

    // Hook appelé depuis Blocks.java (avant le static{} qui peuple BLOCK_STATE_REGISTRY).
    public static void registerBlocks() {
        for (final Spec s : specs()) {
            final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NS, s.name()));
            final Block block;
            if ("wool_stairs".equals(s.archetype())) {
                final Block base = BuiltInRegistries.BLOCK.getValue(Identifier.parse(s.base()));
                final BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(base).setId(key);
                block = new MineazurWoolStairsBlock(base.defaultBlockState(), props);
            } else {
                final BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .mapColor(mapColor(s.mapColor()))
                    .strength(s.hardness(), s.resistance())
                    .sound(soundType(s.sound()));
                if (s.light() > 0) {
                    final int light = s.light();
                    props.lightLevel(state -> light);
                }
                if (!s.occlusion()) {
                    props.noOcclusion();
                }
                props.setId(key);
                block = create(s, props);
            }
            Registry.register(BuiltInRegistries.BLOCK, key, block);
        }
    }

    private static Block create(final Spec s, final BlockBehaviour.Properties props) {
        return switch (s.archetype()) {
            case "simple" -> s.directional() ? new MineazurHorizontalBlock(props) : new Block(props);
            case "chair" -> new ChairBlock(props);
            case "plafond" -> new PlafondBlock(props);
            default -> throw new IllegalStateException("archétype non supporté : " + s.archetype());
        };
    }

    // Hook appelé depuis Items.java (après tous les items vanilla, même ordre que les blocs).
    public static void registerItems() {
        for (final Spec s : specs()) {
            final Identifier id = Identifier.fromNamespaceAndPath(NS, s.name());
            final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            final Block block = BuiltInRegistries.BLOCK.getValue(id);
            final Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().setId(key);
            final BlockItem item = new BlockItem(block, props);
            item.registerBlocks(Item.BY_BLOCK, item);
            Registry.register(BuiltInRegistries.ITEM, key, item);
        }
    }

    private static MapColor mapColor(final String name) {
        return switch (name) {
            case "WOOD" -> MapColor.WOOD;
            case "STONE" -> MapColor.STONE;
            case "SAND" -> MapColor.SAND;
            default -> throw new IllegalStateException("mapColor non mappé : " + name);
        };
    }

    private static SoundType soundType(final String name) {
        return switch (name) {
            case "WOOD" -> SoundType.WOOD;
            case "STONE" -> SoundType.STONE;
            case "GLASS" -> SoundType.GLASS;
            default -> throw new IllegalStateException("sound non mappé : " + name);
        };
    }
}
