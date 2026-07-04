package io.papermc.paper.mineazur;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.MineazurShurikenEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShurikenItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChairBlock;
import net.minecraft.world.level.block.MineazurHorizontalBlock;
import net.minecraft.world.level.block.PlafondBlock;
import net.minecraft.world.level.block.MineazurWoolStairsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.food.FoodProperties;
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

    private static final TagKey<Item> OBSIDIAN_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mineazur", "obsidian_ingots"));

    // Matériau d'outil obsidienne (stats d'origine 2011 : durée 900, efficacité 7.0, ench 12 ; niveau diamant).
    private static final ToolMaterial OBSIDIAN = new ToolMaterial(
        BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 900, 7.0F, 3.0F, 12, OBSIDIAN_INGOTS);

    // Matériau d'armure obsidienne (protection d'origine [3,7,6,3] = boots3/legs6/chest7/helmet3 ; ench 11).
    // assetId mineazur:obsidienne -> textures entity/equipment/humanoid[_leggings]/obsidienne.png (calques habit).
    private static final ArmorMaterial OBSIDIENNE_ARMOR = new ArmorMaterial(
        24,
        new EnumMap<>(Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 7, ArmorType.HELMET, 3, ArmorType.BODY, 11)),
        11, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.0F, 0.0F, OBSIDIAN_INGOTS,
        ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("mineazur", "obsidienne")));

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

    private record ItemSpec(String name, String archetype, int nutrition, float saturation, String slot) {}

    private static List<ItemSpec> itemSpecs() {
        final List<ItemSpec> list = new ArrayList<>();
        try (InputStream in = MineazurGeneratedContent.class.getResourceAsStream("/mineazur_items.json")) {
            if (in == null) {
                return list;
            }
            final JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            final JsonArray items = root.getAsJsonArray("items");
            for (int i = 0; i < items.size(); i++) {
                final JsonObject it = items.get(i).getAsJsonObject();
                list.add(new ItemSpec(
                    it.get("name").getAsString(), it.get("archetype").getAsString(),
                    it.has("nutrition") ? it.get("nutrition").getAsInt() : 0,
                    it.has("saturation") ? it.get("saturation").getAsFloat() : 0.0F,
                    it.has("slot") ? it.get("slot").getAsString() : null));
            }
        } catch (final Exception e) {
            throw new RuntimeException("MineAzur : lecture de mineazur_items.json échouée", e);
        }
        return list;
    }

    // Hook appelé depuis Items.java (après tous les items vanilla, même ordre que le client).
    public static void registerItems() {
        // 1) BlockItems des blocs générés (ordre des blocs).
        for (final Spec s : specs()) {
            final Identifier id = Identifier.fromNamespaceAndPath(NS, s.name());
            final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            final Block block = BuiltInRegistries.BLOCK.getValue(id);
            final Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().setId(key);
            final BlockItem item = new BlockItem(block, props);
            item.registerBlocks(Item.BY_BLOCK, item);
            Registry.register(BuiltInRegistries.ITEM, key, item);
        }
        // 2) Items standalone (mineazur_items.json), APRÈS les BlockItems, même ordre que le client.
        for (final ItemSpec s : itemSpecs()) {
            final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NS, s.name()));
            final Item.Properties props = new Item.Properties();
            switch (s.archetype()) {
                case "food" -> props.food(new FoodProperties.Builder().nutrition(s.nutrition()).saturationModifier(s.saturation()).build());
                case "sword" -> props.sword(OBSIDIAN, 3.0F, -2.4F);
                case "pickaxe" -> props.pickaxe(OBSIDIAN, 1.0F, -2.8F);
                case "axe" -> props.axe(OBSIDIAN, 5.0F, -3.0F);
                case "shovel" -> props.shovel(OBSIDIAN, 1.5F, -3.0F);
                case "hoe" -> props.hoe(OBSIDIAN, -3.0F, 0.0F);
                case "armor" -> props.humanoidArmor(OBSIDIENNE_ARMOR, armorType(s.slot()));
                default -> { /* plain / thrower */ }
            }
            props.setId(key);
            final Item item = "thrower".equals(s.archetype()) ? new ShurikenItem(props) : new Item(props);
            Registry.register(BuiltInRegistries.ITEM, key, item);
        }
    }

    // Hook appelé depuis EntityType.java (après tous les types vanilla). Enregistre l'EntityType du shuriken.
    public static void registerEntities() {
        final ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(NS, "shuriken"));
        final EntityType<MineazurShurikenEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
            EntityType.Builder.<MineazurShurikenEntity>of(MineazurShurikenEntity::new, MobCategory.MISC)
                .noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(key));
        MineazurShurikenEntity.TYPE = type;
    }

    private static ArmorType armorType(final String slot) {
        return switch (slot) {
            case "helmet" -> ArmorType.HELMET;
            case "chestplate" -> ArmorType.CHESTPLATE;
            case "leggings" -> ArmorType.LEGGINGS;
            case "boots" -> ArmorType.BOOTS;
            default -> throw new IllegalStateException("slot d'armure inconnu : " + slot);
        };
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
