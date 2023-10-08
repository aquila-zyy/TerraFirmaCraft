/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.world.settings;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import net.dries007.tfc.common.blocks.SandstoneBlockType;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.blocks.rock.RockCategory;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.world.Codecs;

public record RockSettings(Block raw, Block hardened, Block gravel, Block cobble, Block sand, Block sandstone, Optional<Block> spike, Optional<Block> loose, boolean topLayer, boolean middleLayer, boolean bottomLayer)
{
    public static final Codec<RockSettings> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codecs.BLOCK.fieldOf("raw").forGetter(c -> c.raw),
        Codecs.BLOCK.fieldOf("hardened").forGetter(c -> c.hardened),
        Codecs.BLOCK.fieldOf("gravel").forGetter(c -> c.gravel),
        Codecs.BLOCK.fieldOf("cobble").forGetter(c -> c.cobble),
        Codecs.BLOCK.fieldOf("sand").forGetter(c -> c.sand),
        Codecs.BLOCK.fieldOf("sandstone").forGetter(c -> c.sandstone),
        Codecs.BLOCK.optionalFieldOf("spike").forGetter(c -> c.spike),
        Codecs.BLOCK.optionalFieldOf("loose").forGetter(c -> c.loose),
        Codec.BOOL.fieldOf("top_layer").forGetter(c -> c.topLayer),
        Codec.BOOL.fieldOf("middle_layer").forGetter(c -> c.middleLayer),
        Codec.BOOL.fieldOf("bottom_layer").forGetter(c -> c.bottomLayer)
    ).apply(instance, RockSettings::new));

    private static final Map<ResourceLocation, RockSettings> PRESETS = new ConcurrentHashMap<>();

    public static final Codec<RockSettings> CODEC = Codec.either(
        ResourceLocation.CODEC,
        DIRECT_CODEC
    ).comapFlatMap(
        e -> e.map(
            id -> Codecs.requireNonNull(PRESETS.get(id), () -> "No rock settings for id: " + id),
            DataResult::success
        ),
        Either::right
    );

    /**
     * Register a rock settings preset. This method is safe to call during parallel mod loading.
     */
    public static RockSettings register(ResourceLocation id, RockSettings settings)
    {
        PRESETS.put(id, settings);
        return settings;
    }

    public static void registerDefaultRocks()
    {
        for (Rock rock : Rock.values())
        {
            final ResourceLocation id = Helpers.identifier(rock.getSerializedName());
            final RockCategory category = rock.category();
            final Map<Rock.BlockType, RegistryObject<Block>> blocks = TFCBlocks.ROCK_BLOCKS.get(rock);

            register(id, new RockSettings(
                blocks.get(Rock.BlockType.RAW).get(),
                blocks.get(Rock.BlockType.HARDENED).get(),
                blocks.get(Rock.BlockType.GRAVEL).get(),
                blocks.get(Rock.BlockType.COBBLE).get(),
                TFCBlocks.SAND.get(rock.getSandType()).get(),
                TFCBlocks.SANDSTONE.get(rock.getSandType()).get(SandstoneBlockType.RAW).get(),
                Optional.of(blocks.get(Rock.BlockType.SPIKE).get()),
                Optional.of(blocks.get(Rock.BlockType.LOOSE).get()),
                category != RockCategory.IGNEOUS_INTRUSIVE,
                true,
                category == RockCategory.IGNEOUS_INTRUSIVE || category == RockCategory.METAMORPHIC
            ));
        }
    }

    public boolean isRawOrHardened(BlockState state)
    {
        return Helpers.isBlock(state, raw()) || Helpers.isBlock(state, hardened());
    }
}
