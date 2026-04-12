package dev.moar.util;

/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.AttachFace;
*//*?} else {*/
import net.minecraft.block.enums.BlockFace;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Half;
*//*?} else {*/
import net.minecraft.block.enums.BlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
*//*?} else {*/
import net.minecraft.state.property.Properties;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Property;
*//*?} else {*/
import net.minecraft.state.property.Property;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.Direction;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.shapes.Shapes;
*//*?} else {*/
import net.minecraft.util.shape.VoxelShapes;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/

// Block placement dependency resolution — determines whether a block
// needs adjacent support (floor, wall, ceiling) before it can be placed.
public final class BlockDependency {

    private BlockDependency() {}

    // --- PUBLIC API

    // Required support direction, or null if freestanding.
    public static Direction getRequiredSupport(BlockState state) {
        Block block = state.getBlock();

        // Wall-mounted torches
        if (block instanceof WallTorchBlock
                /*? if >=26.1 {*//*
                || block instanceof RedstoneWallTorchBlock) {
                *//*?} else {*/
                || block instanceof WallRedstoneTorchBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
                /*?}*/
            }
        }
        // Standing torches
        if (block instanceof TorchBlock
                && !(block instanceof WallTorchBlock)) {
            return Direction.DOWN;
        }
        if (block instanceof RedstoneTorchBlock
                /*? if >=26.1 {*//*
                && !(block instanceof RedstoneWallTorchBlock)) {
                *//*?} else {*/
                && !(block instanceof WallRedstoneTorchBlock)) {
                /*?}*/
            return Direction.DOWN;
        }

        // Wall signs
        if (block instanceof WallSignBlock
                || block instanceof WallHangingSignBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
                /*?}*/
            }
        }
        // Standing signs
        if (block instanceof SignBlock
                && !(block instanceof WallSignBlock)) {
            return Direction.DOWN;
        }
        // Hanging signs (chain below a block)
        if (block instanceof HangingSignBlock
                && !(block instanceof WallHangingSignBlock)) {
            return Direction.UP;
        }

        // Banners
        if (block instanceof AbstractBannerBlock
                && !(block instanceof WallBannerBlock)) {
            return Direction.DOWN;
        }
        if (block instanceof WallBannerBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
                /*?}*/
            }
        }

        // Ladders
        if (block instanceof LadderBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
                /*?}*/
            }
        }

        // Lanterns
        if (block instanceof LanternBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HANGING)
            *//*?} else {*/
            if (state.contains(Properties.HANGING)
            /*?}*/
                    /*? if >=26.1 {*//*
                    && state.getValue(BlockStateProperties.HANGING)) {
                    *//*?} else {*/
                    && state.get(Properties.HANGING)) {
                    /*?}*/
                return Direction.UP;
            }
            return Direction.DOWN;
        }

        // Buttons / Levers (floor, wall, ceiling)
        if (block instanceof ButtonBlock || block instanceof LeverBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            *//*?} else {*/
            if (state.contains(Properties.BLOCK_FACE)) {
            /*?}*/
                /*? if >=26.1 {*//*
                AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
                *//*?} else {*/
                BlockFace face = state.get(Properties.BLOCK_FACE);
                /*?}*/
                return switch (face) {
                    case FLOOR   -> Direction.DOWN;
                    case CEILING -> Direction.UP;
                    case WALL    -> {
                        /*? if >=26.1 {*//*
                        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                        *//*?} else {*/
                        if (state.contains(Properties.HORIZONTAL_FACING)) {
                        /*?}*/
                            /*? if >=26.1 {*//*
                            yield state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                            *//*?} else {*/
                            yield state.get(Properties.HORIZONTAL_FACING)
                            /*?}*/
                                    .getOpposite();
                        }
                        yield null;
                    }
                };
            }
        }

        // Skulls / heads
        if (block instanceof WallSkullBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
                /*?}*/
            }
        }
        if (block instanceof SkullBlock
                && !(block instanceof WallSkullBlock)) {
            return Direction.DOWN;
        }

        // Trapdoors
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HALF)) {
            *//*?} else {*/
            if (state.contains(Properties.BLOCK_HALF)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Half half = state.getValue(BlockStateProperties.HALF);
                *//*?} else {*/
                BlockHalf half = state.get(Properties.BLOCK_HALF);
                /*?}*/
                /*? if >=26.1 {*//*
                return half == Half.TOP ? Direction.UP : Direction.DOWN;
                *//*?} else {*/
                return half == BlockHalf.TOP ? Direction.UP : Direction.DOWN;
                /*?}*/
            }
        }

        // Doors / Beds / Tall plants
        if (block instanceof DoorBlock)      return Direction.DOWN;
        if (block instanceof BedBlock)       return Direction.DOWN;
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock) return Direction.DOWN;
        *//*?} else {*/
        if (block instanceof TallPlantBlock) return Direction.DOWN;
        /*?}*/
        // Hoppers
        //  Hopper facing is determined by the clicked face. The block
        //  in the output direction must exist first so we can click it.
        if (block instanceof HopperBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            *//*?} else {*/
            if (state.contains(Properties.HOPPER_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = state.getValue(BlockStateProperties.FACING_HOPPER);
                *//*?} else {*/
                Direction facing = state.get(Properties.HOPPER_FACING);
                /*?}*/
                // DOWN hoppers: need block below (or above) to click
                if (facing == Direction.DOWN) return Direction.DOWN;
                // Horizontal: need the block in the output direction
                return facing;
            }
        }
        // Floor-dependent blocks (previously uncovered)
        if (block instanceof CarpetBlock)               return Direction.DOWN;
        /*? if >=26.1 {*//*
        if (block instanceof BasePressurePlateBlock) return Direction.DOWN;
        *//*?} else {*/
        if (block instanceof AbstractPressurePlateBlock) return Direction.DOWN;
        /*?}*/
        /*? if >=26.1 {*//*
        if (block instanceof BaseRailBlock)          return Direction.DOWN;
        *//*?} else {*/
        if (block instanceof AbstractRailBlock)          return Direction.DOWN;
        /*?}*/
        /*? if >=26.1 {*//*
        if (block instanceof RedStoneWireBlock)          return Direction.DOWN;
        *//*?} else {*/
        if (block instanceof RedstoneWireBlock)          return Direction.DOWN;
        /*?}*/
        /*? if >=26.1 {*//*
        if (block instanceof DiodeBlock)  return Direction.DOWN;
        *//*?} else {*/
        if (block instanceof AbstractRedstoneGateBlock)  return Direction.DOWN;
        /*?}*/
        if (block instanceof FlowerPotBlock)             return Direction.DOWN;
        if (block instanceof CandleBlock)                return Direction.DOWN;
        if (block instanceof SeaPickleBlock)             return Direction.DOWN;

        // Tripwire hooks
        /*? if >=26.1 {*//*
        if (block instanceof TripWireHookBlock) {
        *//*?} else {*/
        if (block instanceof TripwireHookBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
                /*?}*/
            }
        }

        // Amethyst clusters / buds
        if (block instanceof AmethystClusterBlock) {
            /*? if >=26.1 {*//*
            if (state.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (state.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                return state.getValue(BlockStateProperties.FACING).getOpposite();
                *//*?} else {*/
                return state.get(Properties.FACING).getOpposite();
                /*?}*/
            }
        }

        // ── Catch-all: PlantBlock covers flowers, saplings,
        //    ferns, dead bush, crops, mushrooms, etc. ────────────
        //    (checked AFTER TallPlantBlock to avoid shadowing)
        /*? if >=26.1 {*//*
        if (block instanceof BushBlock) return Direction.DOWN;
        *//*?} else {*/
        if (block instanceof PlantBlock) return Direction.DOWN;
        /*?}*/

        // Freestanding — no dependency
        return null;
    }

    // True if placement dependencies are satisfied. Freestanding blocks always true.
    // Vines: true if at least one attached face has support.
    /*? if >=26.1 {*//*
    public static boolean isReadyToPlace(Level world, BlockPos pos,
    *//*?} else {*/
    public static boolean isReadyToPlace(World world, BlockPos pos,
    /*?}*/
                                          BlockState target) {
        Block block = target.getBlock();

        // Vines: multiple attachment faces
        if (block instanceof VineBlock) {
            return hasVineSupport(world, pos, target);
        }

        Direction support = getRequiredSupport(target);
        if (support == null) return true; // freestanding

        /*? if >=26.1 {*//*
        return isSolidAt(world, pos.relative(support));
        *//*?} else {*/
        return isSolidAt(world, pos.offset(support));
        /*?}*/
    }

    /**
     * Returns the dependency tier of a block state.
     *   0 -- freestanding (no dependency)
     *   1 -- needs adjacent support
     * Used in comparator tie-breaking to prioritize freestanding blocks.
     */
    public static int getTier(BlockState state) {
        if (state.getBlock() instanceof VineBlock) return 1;
        return getRequiredSupport(state) == null ? 0 : 1;
    }

    // --- INTERNAL HELPERS

    // True if block at pos is solid (non-replaceable with collision shape).
    /*? if >=26.1 {*//*
    private static boolean isSolidAt(Level world, BlockPos pos) {
    *//*?} else {*/
    private static boolean isSolidAt(World world, BlockPos pos) {
    /*?}*/
        BlockState state = world.getBlockState(pos);
        /*? if >=26.1 {*//*
        return !state.canBeReplaced()
        *//*?} else {*/
        return !state.isReplaceable()
        /*?}*/
                /*? if >=26.1 {*//*
                && state.getShape(world, pos) != Shapes.empty();
                *//*?} else {*/
                && state.getOutlineShape(world, pos) != VoxelShapes.empty();
                /*?}*/
    }

    // Vines attach to wall faces or hang from above. True if any attachment has support.
    /*? if >=26.1 {*//*
    private static boolean hasVineSupport(Level world, BlockPos pos,
    *//*?} else {*/
    private static boolean hasVineSupport(World world, BlockPos pos,
    /*?}*/
                                           BlockState state) {
        /*? if >=26.1 {*//*
        if (hasVineFace(world, pos, state, BlockStateProperties.NORTH, Direction.NORTH)) return true;
        *//*?} else {*/
        if (hasVineFace(world, pos, state, Properties.NORTH, Direction.NORTH)) return true;
        /*?}*/
        /*? if >=26.1 {*//*
        if (hasVineFace(world, pos, state, BlockStateProperties.SOUTH, Direction.SOUTH)) return true;
        *//*?} else {*/
        if (hasVineFace(world, pos, state, Properties.SOUTH, Direction.SOUTH)) return true;
        /*?}*/
        /*? if >=26.1 {*//*
        if (hasVineFace(world, pos, state, BlockStateProperties.EAST,  Direction.EAST))  return true;
        *//*?} else {*/
        if (hasVineFace(world, pos, state, Properties.EAST,  Direction.EAST))  return true;
        /*?}*/
        /*? if >=26.1 {*//*
        if (hasVineFace(world, pos, state, BlockStateProperties.WEST,  Direction.WEST))  return true;
        *//*?} else {*/
        if (hasVineFace(world, pos, state, Properties.WEST,  Direction.WEST))  return true;
        /*?}*/
        // Vines can also hang from the block above
        /*? if >=26.1 {*//*
        return isSolidAt(world, pos.above());
        *//*?} else {*/
        return isSolidAt(world, pos.up());
        /*?}*/
    }

    // True if the vine is attached to the given face AND that face has a solid block.
    /*? if >=26.1 {*//*
    private static boolean hasVineFace(Level world, BlockPos pos,
    *//*?} else {*/
    private static boolean hasVineFace(World world, BlockPos pos,
    /*?}*/
                                        BlockState state,
                                        Property<Boolean> prop,
                                        Direction dir) {
        /*? if >=26.1 {*//*
        if (!state.hasProperty(prop) || !state.getValue(prop)) return false;
        *//*?} else {*/
        if (!state.contains(prop) || !state.get(prop)) return false;
        /*?}*/
        /*? if >=26.1 {*//*
        return isSolidAt(world, pos.relative(dir));
        *//*?} else {*/
        return isSolidAt(world, pos.offset(dir));
        /*?}*/
    }
}
