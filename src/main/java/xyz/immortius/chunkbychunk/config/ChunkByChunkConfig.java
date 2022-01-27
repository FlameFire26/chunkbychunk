package xyz.immortius.chunkbychunk.config;

import xyz.immortius.chunkbychunk.config.system.Name;

/**
 * Root ChunkByChunk configuration
 */
public class ChunkByChunkConfig {

    private static final ChunkByChunkConfig instance = new ChunkByChunkConfig();

    public static ChunkByChunkConfig get() {
        return instance;
    }

    @Name("ChunkGeneration")
    private final GenerationConfig generation = new GenerationConfig();

    @Name("BedrockChest")
    private final BedrockChestConfig bedrockChest = new BedrockChestConfig();

    @Name("WorldForge")
    private final WorldForgeConfig worldForge = new WorldForgeConfig();

    @Name("Gameplay")
    private final GameplayConfig gameplayConfig = new GameplayConfig();

    public GenerationConfig getGeneration() {
        return generation;
    }

    public BedrockChestConfig getBedrockChest() {
        return bedrockChest;
    }

    public WorldForgeConfig getWorldForge() { return worldForge; }

    public GameplayConfig getGameplayConfig() {
        return gameplayConfig;
    }
}
