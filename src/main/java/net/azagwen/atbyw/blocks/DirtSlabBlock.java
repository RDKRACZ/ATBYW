package net.azagwen.atbyw.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.light.ChunkLightProvider;

import java.util.Random;

public class DirtSlabBlock extends SlabBlockSubClass {

    public DirtSlabBlock(Settings settings) {
        super(settings);
    }

    private static boolean canSurvive(BlockState state, WorldView worldView, BlockPos pos) {
        BlockPos blockPos = pos.up();
        BlockState blockState = worldView.getBlockState(blockPos);
        if ((blockState.getFluidState().getLevel() == 8)) {
            return false;
        } else {
            int i = ChunkLightProvider.getRealisticOpacity(worldView, state, pos, blockState, blockPos, Direction.UP, blockState.getOpacity(worldView, blockPos));
            return i < worldView.getMaxLightLevel();
        }
    }

    private static boolean canSpread(BlockState state, WorldView worldView, BlockPos pos) {
        BlockPos blockPos = pos.up();
        return canSurvive(state, worldView, pos) && !worldView.getFluidState(blockPos).isIn(FluidTags.WATER);
    }


    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (world.getLightLevel(pos.up()) >= 9) {
            for(int i = 0; i < 4; ++i) {
                BlockPos blockPos = pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
                BlockState newState = world.getBlockState(pos);

                if (world.getBlockState(blockPos).isOf(Blocks.GRASS_BLOCK) && canSpread(state, world, pos)) {
                    world.setBlockState(pos, copyStates(AtbywBlocks.GRASS_BLOCK_SLAB.getDefaultState(), newState));
                }
            }
        }
    }
}
