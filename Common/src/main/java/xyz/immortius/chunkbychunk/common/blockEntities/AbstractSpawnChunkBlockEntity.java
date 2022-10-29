package xyz.immortius.chunkbychunk.common.blockEntities;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.dimension.DimensionType;
import xyz.immortius.chunkbychunk.common.blocks.AbstractTriggeredSpawnChunkBlock;
import xyz.immortius.chunkbychunk.common.world.ControllableChunkMap;
import xyz.immortius.chunkbychunk.common.world.SkyChunkGenerator;
import xyz.immortius.chunkbychunk.common.world.SpawnChunkHelper;
import xyz.immortius.chunkbychunk.interop.Services;

import java.util.function.Function;

/**
 * Base class for all chunk spawning block entities. These block entities wait a short period so that entities can spawn
 * in the generation dimension before spawning a chunk.
 */
public abstract class AbstractSpawnChunkBlockEntity extends BlockEntity {

    private static final int TICKS_TO_SPAWN_CHUNK = 1;
    private static final int TICKS_TO_SYNCH_CHUNK = 3;
    private static final int TICKS_TO_SPAWN_ENTITIES = 20;

    private final Function<BlockPos, ChunkPos> sourceChunkPosFunc;
    private int tickCounter = 0;

    public AbstractSpawnChunkBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state, Function<BlockPos, ChunkPos> sourceChunkPosFunc) {
        super(blockEntityType, pos, state);
        this.sourceChunkPosFunc = sourceChunkPosFunc;
    }

    public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, AbstractSpawnChunkBlockEntity entity) {
        ServerLevel serverLevel = (ServerLevel) level;
        // If there are no players, entities won't spawn. So don't tick.
        if (serverLevel.getPlayers((p) -> true).isEmpty()) {
            return;
        }
        if (blockState.getBlock() instanceof AbstractTriggeredSpawnChunkBlock spawnBlock) {
            entity.tickCounter++;
            ServerLevel sourceLevel = serverLevel.getServer().getLevel(spawnBlock.getSourceLevel(serverLevel));
            if (!spawnBlock.validForLevel(serverLevel) || sourceLevel == null) {
                serverLevel.setBlock(blockPos, serverLevel.getBlockState(blockPos.north()), Block.UPDATE_ALL);
                return;
            }

            ChunkPos targetChunkPos = new ChunkPos(blockPos);
            ChunkPos sourceChunkPos = entity.sourceChunkPosFunc.apply(blockPos);
            if (entity.tickCounter == TICKS_TO_SPAWN_CHUNK) {
                spawnChunk(sourceLevel, sourceChunkPos, serverLevel, targetChunkPos);
            } else if (entity.tickCounter == TICKS_TO_SYNCH_CHUNK) {
                if (synchChunks(serverLevel, targetChunkPos)) {
                    // Only synch one chunk a tick, so add an extra tick
                    entity.tickCounter--;
                }
            } else if (entity.tickCounter >= TICKS_TO_SPAWN_ENTITIES) {
                SpawnChunkHelper.spawnChunkEntities(serverLevel, targetChunkPos, sourceLevel, entity.sourceChunkPosFunc.apply(blockPos));
                if (serverLevel.getBlockState(blockPos) == blockState) {
                    serverLevel.setBlock(blockPos, serverLevel.getBlockState(blockPos.north()), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static boolean synchChunks(ServerLevel targetLevel, ChunkPos targetChunkPos) {
        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            for (ResourceKey<Level> synchLevelId : generator.getSynchedLevels()) {
                ServerLevel synchLevel = targetLevel.getServer().getLevel(synchLevelId);
                double scale = DimensionType.getTeleportationScale(targetLevel.dimensionType(), synchLevel.dimensionType());
                BlockPos pos = targetChunkPos.getMiddleBlockPosition(0);
                ChunkPos synchChunk = new ChunkPos(new BlockPos(pos.getX() * scale, 0, pos.getZ() * scale));
                if (SpawnChunkHelper.isEmptyChunk(synchLevel, synchChunk) && !(synchLevel.getBlockState(synchChunk.getMiddleBlockPosition(synchLevel.getMaxBuildHeight() - 1)).getBlock() instanceof AbstractTriggeredSpawnChunkBlock)) {
                    BlockPos genBlockPos = synchChunk.getMiddleBlockPosition(synchLevel.getMaxBuildHeight() - 1);
                    synchLevel.setBlock(genBlockPos, Services.PLATFORM.triggeredSpawnChunkBlock().defaultBlockState(), Block.UPDATE_ALL);
                    return true;
                }
            }
        }
        return false;
    }

    private static void spawnChunk(ServerLevel sourceLevel, ChunkPos sourceChunkPos, ServerLevel targetLevel, ChunkPos targetChunkPos) {
        if (SpawnChunkHelper.isEmptyChunk(targetLevel, targetChunkPos)) {
            ChunkAccess sourceChunk = sourceLevel.getChunk(sourceChunkPos.x, sourceChunkPos.z);
            ChunkAccess targetChunk = targetLevel.getChunk(targetChunkPos.x, targetChunkPos.z);

            for (int i = 0; i < sourceChunk.getSections().length; i++) {
                PalettedContainer<Holder<Biome>> sourceBiomes = sourceChunk.getSections()[i].getBiomes();
                PalettedContainer<Holder<Biome>> targetBiomes = targetChunk.getSections()[i].getBiomes();

                byte[] buffer = new byte[sourceBiomes.getSerializedSize()];
                FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(buffer));
                friendlyByteBuf.writerIndex(0);
                sourceBiomes.write(friendlyByteBuf);
                friendlyByteBuf.readerIndex(0);
                targetBiomes.read(friendlyByteBuf);
                targetChunk.setUnsaved(true);
            }
            SpawnChunkHelper.spawnChunkBlocks(targetLevel, targetChunkPos, sourceLevel, sourceChunkPos);
            ((ControllableChunkMap) targetLevel.getChunkSource().chunkMap).forceReloadChunk(targetChunkPos);
        }
    }
}
