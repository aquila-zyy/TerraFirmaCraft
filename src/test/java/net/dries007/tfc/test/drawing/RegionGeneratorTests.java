/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.test.drawing;

import java.awt.Color;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import net.dries007.tfc.data.providers.BuiltinWorldPreset;
import net.dries007.tfc.test.TestSetup;
import net.dries007.tfc.util.climate.KoppenClimateClassification;
import net.dries007.tfc.world.region.ChooseRocks;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.RegionGenerator.Task;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.settings.Settings;

import static net.dries007.tfc.world.layer.TFCLayers.*;

@SuppressWarnings("SameParameterValue")
public class RegionGeneratorTests implements TestSetup
{
    final DoubleFunction<Color> blue = Artist.Colors.linearGradient(
        new Color(50, 50, 150),
        new Color(100, 140, 255));

    final DoubleFunction<Color> green = Artist.Colors.linearGradient(
        new Color(0, 100, 0),
        new Color(80, 200, 80));

    final DoubleFunction<Color> temperature = Artist.Colors.multiLinearGradient(
        new Color(180, 20, 240),
        new Color(0, 180, 240),
        new Color(180, 180, 220),
        new Color(210, 210, 0),
        new Color(200, 120, 60),
        new Color(200, 40, 40));

    @Test
    public void testRegionGenerator()
    {
        // Coordinates are given in grid scale, so 1 px = 128 blocks, 150 ~ 20km
        drawStitchedRegions("", EnumSet.allOf(DrawnTask.class), RandomSupport.generateUniqueSeed(), 0, 75, 200);
    }

    private void drawStitchedRegions(String name, Set<DrawnTask> tasksToDraw, long seed, int centerX, int centerZ, int radius)
    {
        record Pos(int x, int z) {}

        final Map<Task, List<DrawnTask>> taskParentMap = tasksToDraw
            .stream()
            .collect(Collectors.groupingBy(t -> t.root));

        final Settings settings = BuiltinWorldPreset.defaultSettings();
        final RegionGenerator generator = new RegionGenerator(settings, new XoroshiroRandomSource(seed));
        final Set<Pos> points = new HashSet<>();
        final Map<DrawnTask, Map<Pos, Color>> drawn = new HashMap<>();

        for (int x = centerX - radius; x <= centerX + radius; x++)
            for (int z = centerZ - radius; z <= centerZ + radius; z++)
                points.add(new Pos(x, z));

        @Nullable Pos pos;
        while ((pos = points.stream().findFirst().orElse(null)) != null)
        {
            generator.visualizeRegion(pos.x, pos.z, (task, region) -> {
                for (DrawnTask drawnTask : taskParentMap.getOrDefault(task, List.of()))
                {
                    final Map<Pos, Color> drawnTaskData = drawn.computeIfAbsent(drawnTask, key -> new HashMap<>());
                    for (final var point : region.points())
                    {
                        final Pos at = new Pos(point.x, point.z);
                        points.remove(at);
                        drawnTaskData.put(at, taskColor(drawnTask, region, point.x, point.z));
                    }
                }
            });
        }

        drawn.forEach((task, drawnTask) -> Artist.raw()
            .center(centerX, centerZ, radius)
            .size(radius * 2 * (task == DrawnTask.ADD_RIVERS_AND_LAKES ? 5 : 1))
            .draw(taskName(name, task), task == DrawnTask.ADD_RIVERS_AND_LAKES
                ? drawWithRivers(generator, task)
                : Artist.Pixel.coerceInt((x, z) -> drawnTask.get(new Pos(x, z)))));
    }

    private Artist.Pixel<Color> drawWithRivers(RegionGenerator generator, DrawnTask task)
    {
        return (xi, zi) -> {
            final int x = (int) xi, z = (int) zi;
            final float xf = (float) xi, zf = (float) zi;
            final Region region = generator.getOrCreateRegion(x, z);
            final Region.Point point = generator.getOrCreateRegionPoint(x, z);

            // Early exit - we don't draw rivers over oceans
            if (point.land() || point.shore())
            {
                for (RiverEdge edge : generator.getOrCreatePartitionPoint(x, z).rivers())
                {
                    if (edge.fractal().intersect(xf, zf, 0.1f)) // Use a slightly larger distance than is typical, so we draw it more visibly
                    {
                        return new Color(100, 210, 250);
                    }
                }
            }
            return taskColor(task, region, x, z);
        };
    }

    private String taskName(String name, DrawnTask task)
    {
        return "region%s_%02d_%s".formatted(name, task.ordinal(), task.name().toLowerCase(Locale.ROOT));
    }

    private Color taskColor(DrawnTask task, Region region, int x, int y)
    {
        final Region.Point point = region.at(x, y);
        assert point != null;
        return switch (task)
        {
            case ADD_CONTINENTS, FLOOD_FILL_SMALL_OCEANS, ADD_ISLANDS -> point.land()
                ? new Color(0, 130, 0)
                : cellColor(region);
            case ANNOTATE_DISTANCE_TO_CELL_EDGE -> blue.apply(point.distanceToEdge / 24f);
            case ANNOTATE_DISTANCE_TO_OCEAN -> point.land()
                ? green.apply(point.distanceToOcean / 20f)
                : cellColor(region);
            case ANNOTATE_BASE_LAND_HEIGHT -> continentColor(point);
            case ADD_MOUNTAINS -> {
                if (!point.mountain()) yield continentColor(point);
                yield point.baseLandHeight <= 2
                    ? new Color(240, 110, 50)
                    : new Color(150, 150, 150);
            }
            case ANNOTATE_DISTANCE_TO_WEST_COAST -> point.land()
                ? green.apply(point.distanceToWestCoast / 100f)
                : cellColor(region);
            case ANNOTATE_BIOME_ALTITUDE -> point.land()
                ? green.apply(Mth.clampedMap(point.discreteBiomeAltitude(), 0, 3, 0, 1))
                : continentColor(point);
            case TEMPERATURE -> temperatureGradient(point, point.temperature, -25f, 35f);
            case RAINFALL, RAINFALL_AFTER_RIVERS -> temperatureGradient(point, point.rainfall, 0, 500);
            case RAINFALL_VARIANCE -> temperatureGradient(point, point.rainfallVariance, -1, 1);
            case KOPPEN, KOPPEN_AFTER_RIVERS -> point.land()
                ? koppenClimateColor(KoppenClimateClassification.classify(point.temperature, point.rainfall, point.rainfallVariance))
                : continentColor(point);
            case CHOOSE_BIOMES -> biomeColor(point.biome);
            case CHOOSE_ROCKS -> {
                final double value = new Random(point.rock >> 2).nextDouble();
                yield switch (point.rock & 0b11)
                {
                    case ChooseRocks.OCEAN -> blue.apply(value);
                    case ChooseRocks.LAND -> green.apply(value);
                    case ChooseRocks.VOLCANIC -> new Color(200, (int) (100 * value), 100);
                    case ChooseRocks.UPLIFT -> new Color(180, (int) (180 * value), 200);
                    default -> throw new RuntimeException("value: " + point.rock);
                };
            }
            case ADD_RIVERS_AND_LAKES -> {
                if (point.river()) yield new Color(120, 120, 240);
                if (point.shore()) yield new Color(120, 120, 240);
                if (point.lake()) yield new Color(150, 160, 255);
                yield continentColor(point);
            }
            case KAOLINITE_CAN_SPAWN -> point.temperature > 18f && point.rainfall > 300 && point.land()
                ? point.biome == HIGHLANDS || point.biome == PLATEAU || point.biome == OLD_MOUNTAINS || point.biome == ROLLING_HILLS
                    ? Color.MAGENTA
                    : Color.PINK
                : continentColor(point);
        };
    }

    private Color cellColor(Region region)
    {
        return blue.apply(0.5 + 0.5 * region.noise());
    }

    private Color continentColor(Region.Point point)
    {
        if (point.land()) return green.apply(point.baseLandHeight / 24f);
        if (point.baseOceanDepth < 4) return new Color(150, 160, 255);
        if (point.baseOceanDepth < 8) return new Color(120, 120, 240);
        return new Color(100, 100, 200);
    }

    private Color temperatureGradient(Region.Point point, float value, float min, float max)
    {
        return (point.land() ? temperature : blue).apply(Mth.clampedMap(value, min, max, 0f, 0.999f));
    }

    private Color biomeColor(int biome)
    {
        if (biome == OCEAN) return new Color(0, 0, 220);
        if (biome == OCEAN_REEF) return new Color(70, 160, 250);
        if (biome == DEEP_OCEAN) return new Color(0, 0, 160);
        if (biome == DEEP_OCEAN_TRENCH) return new Color(0, 0, 80);
        if (biome == LAKE) return new Color(30, 30, 255);
        if (biome == MOUNTAIN_LAKE || biome == OCEANIC_MOUNTAIN_LAKE || biome == OLD_MOUNTAIN_LAKE || biome == VOLCANIC_MOUNTAIN_LAKE || biome == PLATEAU_LAKE) return new Color(180, 180, 255);
        if (biome == RIVER) return new Color(0, 200, 255);

        if (biome == OCEANIC_MOUNTAINS || biome == VOLCANIC_OCEANIC_MOUNTAINS) return new Color(255, 0, 255);
        if (biome == CANYONS) return new Color(180, 60, 255);
        if (biome == LOW_CANYONS) return new Color(200, 110, 255);
        if (biome == LOWLANDS) return new Color(220, 150, 230);

        if (biome == MOUNTAINS || biome == VOLCANIC_MOUNTAINS) return new Color(255, 50, 50);
        if (biome == OLD_MOUNTAINS) return new Color(240, 100, 100);
        if (biome == PLATEAU) return new Color(210, 120, 120);

        if (biome == BADLANDS) return new Color(255, 150, 0);
        if (biome == INVERTED_BADLANDS) return new Color(240, 180, 0);

        if (biome == SHORE) return new Color(230, 210, 130);

        if (biome == HIGHLANDS) return new Color(20, 80, 30);
        if (biome == ROLLING_HILLS) return new Color(50, 100, 50);
        if (biome == HILLS) return new Color(80, 130, 80);
        if (biome == PLAINS) return new Color(100, 200, 100);

        return Color.BLACK;
    }

    /**
     * Colors matched to the map on the <a href="https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification#/media/File:Koppen-Geiger_Map_v2_World_1991%E2%80%932020.svg">Koppen Climate Wikipedia</a> page.
     */
    private Color koppenClimateColor(KoppenClimateClassification koppen)
    {
        return switch (koppen)
        {
            case AF -> new Color(0, 0, 220);
            case AS -> new Color(0, 100, 240);
            case AW -> new Color(0, 150, 220);
            case AM -> new Color(40, 80, 200);
            case BWH -> new Color(210, 0, 0);
            case BSH -> new Color(210, 120, 0);
            case BWK -> new Color(200, 80, 80);
            case BSK -> new Color(200, 120, 60);
            case CSA -> new Color(250, 250, 0);
            case CSB -> new Color(180, 180, 0);
            case CSC -> new Color(120, 120, 0);
            case CWA -> new Color(100, 240, 130);
            case CWB -> new Color(80, 210, 120);
            case CWC -> new Color(70, 160, 110);
            case CFA -> new Color(170, 240, 90);
            case CFB -> new Color(140, 200, 80);
            case CFC -> new Color(110, 170, 70);
            case DSA -> new Color(190, 20, 190);
            case DSB -> new Color(160, 20, 180);
            case DSC -> new Color(130, 20, 170);
            case DSD -> new Color(100, 20, 160);
            case DFA -> new Color(40, 190, 190);
            case DFB -> new Color(30, 170, 170);
            case DFC -> new Color(20, 150, 140);
            case DFD -> new Color(10, 130, 110);
            case DWA -> new Color(80, 80, 220);
            case DWB -> new Color(70, 70, 190);
            case DWC -> new Color(60, 60, 160);
            case DWD -> new Color(60, 60, 130);
            case ET -> new Color(190, 190, 190);
            case EF -> new Color(80, 80, 80);
        };
    }

    /**
     * Allows drawing additional visualizations between generation tasks.
     */
    enum DrawnTask
    {
        ADD_CONTINENTS(Task.ADD_CONTINENTS),
        ANNOTATE_DISTANCE_TO_CELL_EDGE(Task.ANNOTATE_DISTANCE_TO_CELL_EDGE),
        FLOOD_FILL_SMALL_OCEANS(Task.FLOOD_FILL_SMALL_OCEANS),
        ADD_ISLANDS(Task.ADD_ISLANDS),
        ANNOTATE_DISTANCE_TO_OCEAN(Task.ANNOTATE_DISTANCE_TO_OCEAN),
        ANNOTATE_BASE_LAND_HEIGHT(Task.ANNOTATE_BASE_LAND_HEIGHT),
        ANNOTATE_DISTANCE_TO_WEST_COAST(Task.ANNOTATE_DISTANCE_TO_WEST_COAST),
        ADD_MOUNTAINS(Task.ADD_MOUNTAINS),
        ANNOTATE_BIOME_ALTITUDE(Task.ANNOTATE_BIOME_ALTITUDE),
        // Multiple steps to draw temperature, rainfall, and rainfall variance
        TEMPERATURE(Task.ANNOTATE_CLIMATE),
        RAINFALL(Task.ANNOTATE_CLIMATE),
        RAINFALL_VARIANCE(Task.ANNOTATE_CLIMATE),
        KOPPEN(Task.ANNOTATE_CLIMATE),

        CHOOSE_BIOMES(Task.CHOOSE_BIOMES),
        CHOOSE_ROCKS(Task.CHOOSE_ROCKS),
        ADD_RIVERS_AND_LAKES(Task.ADD_RIVERS_AND_LAKES),
        // Draw climate visualizations again after rivers, which modify rainfall
        RAINFALL_AFTER_RIVERS(Task.ADD_RIVERS_AND_LAKES),
        KOPPEN_AFTER_RIVERS(Task.ADD_RIVERS_AND_LAKES),
        // Visualize where things can spawn
        KAOLINITE_CAN_SPAWN(Task.ADD_RIVERS_AND_LAKES),
        ;

        final Task root;

        DrawnTask(Task root)
        {
            this.root = root;
        }
    }
}
