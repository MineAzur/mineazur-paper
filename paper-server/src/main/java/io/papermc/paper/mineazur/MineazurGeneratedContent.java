package io.papermc.paper.mineazur;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShurikenItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChairBlock;
import net.minecraft.world.level.block.CoinDeToitBlock;
import net.minecraft.world.level.block.DiamondLampBlock;
import net.minecraft.world.level.block.MineazurHorizontalBlock;
import net.minecraft.world.level.block.PlafondBlock;
import net.minecraft.world.level.block.MineazurWoolStairsBlock;
import net.minecraft.world.level.block.SangMurBlock;
import net.minecraft.world.level.block.SangMurPerissableBlock;
import net.minecraft.world.level.block.SangSolBlock;
import net.minecraft.world.level.block.SangSolPerissableBlock;
import net.minecraft.world.level.block.TombeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

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

    // Matériau du COSTUME Zehir 2012 (soulier/pentalon/smoking) : COSMÉTIQUE — protection [0,0,0,0], ench 0, non
    // réparable (tag vide). Seule compte l'apparence portée (assetId mineazur:costume -> calques equipment). Évite
    // tout déséquilibre PvP (décision B3).
    private static final TagKey<Item> COSTUME_REPAIR = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mineazur", "costume_repair"));
    private static final ArmorMaterial COSTUME_ARMOR = new ArmorMaterial(
        15,
        new EnumMap<>(Map.of(ArmorType.BOOTS, 0, ArmorType.LEGGINGS, 0, ArmorType.CHESTPLATE, 0, ArmorType.HELMET, 0, ArmorType.BODY, 0)),
        1, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, COSTUME_REPAIR, // ench=1 (>0 requis : Enchantable interdit 0)
        ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("mineazur", "costume")));

    private record Spec(String name, String archetype, boolean directional, boolean occlusion, int light,
                        float hardness, float resistance, String sound, String mapColor, String base,
                        boolean perishable, String wallVariant, boolean noItem) {}

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
                        asString(p, "base", null),
                        p.has("perishable") && p.get("perishable").getAsBoolean(),
                        asString(p, "wallVariant", null),
                        p.has("noItem") && p.get("noItem").getAsBoolean()
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
                if (s.archetype().startsWith("connected")) {
                    // Décal plat non-solide (peint au sol ou sur une paroi) : traversable + détruit par un piston
                    // (comme la redstone). « Éternel » ne concerne QUE le délavage, pas les pistons.
                    props.noCollision().pushReaction(PushReaction.DESTROY);
                    if (s.perishable()) {
                        // Le délavage passe par randomTick : seuls les périssables en ont besoin (SangBlock.delaverTick).
                        props.randomTicks();
                    }
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
            // Famille sang : le périssable est une SOUS-CLASSE (il porte AGE en plus), pas un booléen — cf.
            // l'en-tête de SangBlock, createBlockStateDefinition tourne avant l'init des champs d'instance.
            case "connected" -> s.perishable() ? new SangSolPerissableBlock(props) : new SangSolBlock(props);
            case "connected_wall" -> s.perishable() ? new SangMurPerissableBlock(props) : new SangMurBlock(props);
            case "lamp" -> new DiamondLampBlock(props);
            case "coin_toit" -> new CoinDeToitBlock(props);
            case "tombe" -> new TombeBlock(props);
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
            // Les décals muraux n'ont PAS d'item propre : ils sont posés par l'item du bloc de sol (ci-dessous).
            // ⚠️ Ce `continue` saute un ID d'item, et les IDs d'items CIRCULENT sur le réseau (contrairement aux
            // blocs, dont seuls les IDs d'ÉTAT transitent) : le skip doit être MIROIR STRICT côté client, sinon
            // tous les items suivants sont décalés.
            if (s.noItem()) {
                continue;
            }
            final Identifier id = Identifier.fromNamespaceAndPath(NS, s.name());
            final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            final Block block = BuiltInRegistries.BLOCK.getValue(id);
            final Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().setId(key);
            final BlockItem item;
            if (s.wallVariant() != null) {
                // Pattern torch/wall_torch : un seul item qui pose le décal au sol ou sur la paroi selon la face
                // cliquée (Items.java:392). Direction.DOWN = le bloc « debout » s'attache vers le bas.
                final Block wall = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NS, s.wallVariant()));
                item = new StandingAndWallBlockItem(block, wall, Direction.DOWN, props);
            } else {
                item = new BlockItem(block, props);
            }
            // registerBlocks() mappe AUSSI le wallBlock vers cet item dans BY_BLOCK — ce qui sert au pick-block
            // (Item.byBlock), PAS au drop : le drop reste décidé par la loot table du bloc mural, qui rend donc
            // explicitement l'item de sol (data/mineazur/loot_table/blocks/sang_mur.json).
            item.registerBlocks(Item.BY_BLOCK, item);
            Registry.register(BuiltInRegistries.ITEM, key, item);
        }
        // 2) Items standalone (mineazur_items.json), APRÈS les BlockItems, même ordre que le client.
        for (final ItemSpec s : itemSpecs()) {
            final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NS, s.name()));
            final Item.Properties props = new Item.Properties();
            switch (s.archetype()) {
                case "food" -> {
                    if ("biere".equals(s.name())) {
                        configureBiere(props, s);
                    } else {
                        props.food(new FoodProperties.Builder().nutrition(s.nutrition()).saturationModifier(s.saturation()).build());
                    }
                }
                case "sword" -> props.sword(OBSIDIAN, 3.0F, -2.4F);
                case "pickaxe" -> props.pickaxe(OBSIDIAN, 1.0F, -2.8F);
                case "axe" -> props.axe(OBSIDIAN, 5.0F, -3.0F);
                case "shovel" -> props.shovel(OBSIDIAN, 1.5F, -3.0F);
                case "hoe" -> props.hoe(OBSIDIAN, -3.0F, 0.0F);
                case "armor" -> props.humanoidArmor(OBSIDIENNE_ARMOR, armorType(s.slot()));
                case "cosmetic" -> props.humanoidArmor(COSTUME_ARMOR, armorType(s.slot()));
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

    // Hook appelé depuis le schéma DataFixerUpper le plus récent qui déclare des entités (V4656).
    //
    // POURQUOI : EntityType.Builder.build() appelle Util.fetchChoiceType(ENTITY_TREE, id) pour toute entité
    // « serialize » ; un id absent du schéma logge « No data fixer registered for mineazur:shuriken » (et
    // LÈVE en IDE, cf. Util.doFetchChoiceType). Sans déclaration, aucune règle ne migrerait le NBT des
    // shurikens déjà posés en monde le jour d'un bump de version MC.
    //
    // FORME : MineazurShurikenEntity étend Snowball, donc ThrowableItemProjectile, qui sérialise TOUJOURS son
    // ItemStack sous « Item » (ThrowableItemProjectile.addAdditionalSaveData). On calque donc splash_potion
    // (V4306) — optionalFields("Item", ITEM_STACK) — et NON snowball, que vanilla déclare en registerSimple :
    // l'item d'un snowball est toujours minecraft:snowball, le nôtre est un item CUSTOM dont l'ItemStack
    // imbriqué doit rester migrable.
    public static void registerDataFixerEntities(final Schema schema, final Map<String, Supplier<TypeTemplate>> map) {
        schema.register(map, NS + ":shuriken", () -> DSL.optionalFields("Item", References.ITEM_STACK.in(schema)));
    }

    // Bière (portage de tboss.SBTBlocks.ItemBiere, 2011) : à la consommation « soûle » le joueur (60 s) puis
    // se transforme en chope vide. L'original appliquait l'effet 9 (Nausée) + l'effet CUSTOM 20 « potion.drunk »
    // (cosmétique, SANS dégâts) — et NON le Wither vanilla. Le port traduisait id 20 → MobEffects.WITHER (ampli 25),
    // ce qui tuait le joueur : bug. On restitue le gag « bourré » par la seule Nausée (le wobble d'écran).
    private static void configureBiere(final Item.Properties props, final ItemSpec s) {
        final FoodProperties food = new FoodProperties.Builder()
            .nutrition(s.nutrition()).saturationModifier(s.saturation()).alwaysEdible().build();
        final Consumable consumable = Consumables.defaultDrink()
            .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.NAUSEA, 1200, 10))))
            .build();
        props.food(food, consumable).stacksTo(1);
        final Item chope = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(NS, "chope"));
        if (chope != null && chope != Items.AIR) {
            props.usingConvertsTo(chope);
        }
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
            case "NETHER" -> MapColor.NETHER;
            case "BLUE" -> MapColor.COLOR_BLUE;
            case "RED" -> MapColor.COLOR_RED;
            case "DIAMOND" -> MapColor.DIAMOND;
            default -> throw new IllegalStateException("mapColor non mappé : " + name);
        };
    }

    private static SoundType soundType(final String name) {
        return switch (name) {
            case "WOOD" -> SoundType.WOOD;
            case "STONE" -> SoundType.STONE;
            case "GLASS" -> SoundType.GLASS;
            case "WOOL" -> SoundType.WOOL;
            default -> throw new IllegalStateException("sound non mappé : " + name);
        };
    }
}
