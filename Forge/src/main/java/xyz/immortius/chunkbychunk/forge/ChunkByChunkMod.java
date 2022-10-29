package xyz.immortius.chunkbychunk.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import xyz.immortius.chunkbychunk.client.screens.BedrockChestScreen;
import xyz.immortius.chunkbychunk.client.screens.WorldForgeScreen;
import xyz.immortius.chunkbychunk.client.screens.WorldScannerScreen;
import xyz.immortius.chunkbychunk.common.CommonEventHandler;
import xyz.immortius.chunkbychunk.common.blockEntities.*;
import xyz.immortius.chunkbychunk.common.blocks.*;
import xyz.immortius.chunkbychunk.common.commands.SpawnChunkCommand;
import xyz.immortius.chunkbychunk.common.data.ScannerData;
import xyz.immortius.chunkbychunk.common.menus.BedrockChestMenu;
import xyz.immortius.chunkbychunk.common.menus.WorldForgeMenu;
import xyz.immortius.chunkbychunk.common.menus.WorldScannerMenu;
import xyz.immortius.chunkbychunk.common.world.NetherChunkByChunkGenerator;
import xyz.immortius.chunkbychunk.common.world.SkyChunkGenerator;
import xyz.immortius.chunkbychunk.config.ChunkByChunkConfig;
import xyz.immortius.chunkbychunk.config.system.ConfigSystem;
import xyz.immortius.chunkbychunk.common.ChunkByChunkConstants;
import xyz.immortius.chunkbychunk.server.ServerEventHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Forge mod, registers all mod elements for forge
 */
@Mod(ChunkByChunkConstants.MOD_ID)
public class ChunkByChunkMod {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ChunkByChunkConstants.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ChunkByChunkConstants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ChunkByChunkConstants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ChunkByChunkConstants.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ChunkByChunkConstants.MOD_ID);

    public static final RegistryObject<Block> TRIGGERED_SPAWN_CHUNK_BLOCK = BLOCKS.register("triggeredchunkspawner", () -> new TriggeredSpawnChunkBlock(ChunkByChunkConstants.SKY_CHUNK_GENERATION_LEVEL, BlockBehaviour.Properties.of(Material.AIR)));
    public static final RegistryObject<Block> TRIGGERED_SPAWN_RANDOM_CHUNK_BLOCK = BLOCKS.register("triggeredrandomchunkspawner", () -> new TriggeredSpawnRandomChunkBlock(ChunkByChunkConstants.SKY_CHUNK_GENERATION_LEVEL, BlockBehaviour.Properties.of(Material.AIR)));

    public static final RegistryObject<Block> SPAWN_CHUNK_BLOCK = BLOCKS.register("chunkspawner", () -> new SpawnChunkBlock(TRIGGERED_SPAWN_CHUNK_BLOCK.get(), BlockBehaviour.Properties.of(Material.STONE)));
    public static final RegistryObject<Block> UNSTABLE_SPAWN_CHUNK_BLOCK = BLOCKS.register("unstablechunkspawner", () -> new UnstableSpawnChunkBlock(BlockBehaviour.Properties.of(Material.STONE)));
    public static final RegistryObject<Block> BEDROCK_CHEST_BLOCK = BLOCKS.register("bedrockchest", () -> new BedrockChestBlock(BlockBehaviour.Properties.of(Material.STONE).strength(-1, 3600000.0F).noLootTable().isValidSpawn(((p_61031_, p_61032_, p_61033_, p_61034_) -> false))));
    public static final RegistryObject<Block> WORLD_CORE_BLOCK = BLOCKS.register("worldcore", () -> new Block(BlockBehaviour.Properties.of(Material.STONE).strength(3.0F).lightLevel((state) -> 7)));
    public static final RegistryObject<Block> WORLD_FORGE_BLOCK = BLOCKS.register("worldforge", () -> new WorldForgeBlock(BlockBehaviour.Properties.of(Material.STONE).strength(3.5F).lightLevel((state) -> 7)));
    public static final RegistryObject<Block> WORLD_SCANNER_BLOCK = BLOCKS.register("worldscanner", () -> new WorldScannerBlock(BlockBehaviour.Properties.of(Material.STONE).strength(3.5F).lightLevel((state) -> 4)));

    public static final RegistryObject<Item> SPAWN_CHUNK_BLOCK_ITEM = ITEMS.register("chunkspawner", () -> new BlockItem(SPAWN_CHUNK_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> UNSTABLE_SPAWN_CHUNK_BLOCK_ITEM = ITEMS.register("unstablechunkspawner", () -> new BlockItem(UNSTABLE_SPAWN_CHUNK_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> BEDROCK_CHEST_ITEM = ITEMS.register("bedrockchest", () -> new BlockItem(BEDROCK_CHEST_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> WORLD_CORE_BLOCK_ITEM = ITEMS.register("worldcore", () -> new BlockItem(WORLD_CORE_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> WORLD_FORGE_BLOCK_ITEM = ITEMS.register("worldforge", () -> new BlockItem(WORLD_FORGE_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> WORLD_SCANNER_BLOCK_ITEM = ITEMS.register("worldscanner", () -> new BlockItem(WORLD_SCANNER_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> WORLD_FRAGMENT_ITEM = ITEMS.register("worldfragment", () -> new Item(new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> WORLD_SHARD_ITEM = ITEMS.register("worldshard", () -> new Item(new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
    public static final RegistryObject<Item> WORLD_CRYSTAL_ITEM = ITEMS.register("worldcrystal", () -> new Item(new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<BlockEntityType<?>> BEDROCK_CHEST_BLOCK_ENTITY = BLOCK_ENTITIES.register("bedrockchestentity", () -> BlockEntityType.Builder.of(BedrockChestBlockEntity::new, BEDROCK_CHEST_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<?>> WORLD_FORGE_BLOCK_ENTITY = BLOCK_ENTITIES.register("worldforgeentity", () -> BlockEntityType.Builder.of(WorldForgeBlockEntity::new, WORLD_FORGE_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<?>> WORLD_SCANNER_BLOCK_ENTITY = BLOCK_ENTITIES.register("worldscannerentity", () -> BlockEntityType.Builder.of(WorldScannerBlockEntity::new, WORLD_SCANNER_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<?>> TRIGGERED_SPAWN_CHUNK_BLOCK_ENTITY;
    public static final RegistryObject<BlockEntityType<?>> TRIGGERED_SPAWN_RANDOM_CHUNK_BLOCK_ENTITY = BLOCK_ENTITIES.register("triggeredspawnrandomchunkentity", () -> BlockEntityType.Builder.of(TriggeredSpawnRandomChunkBlockEntity::new, TRIGGERED_SPAWN_RANDOM_CHUNK_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<BedrockChestMenu>> BEDROCK_CHEST_MENU = CONTAINERS.register("bedrockchestmenu", () -> new MenuType<>(BedrockChestMenu::new));
    public static final RegistryObject<MenuType<WorldForgeMenu>> WORLD_FORGE_MENU = CONTAINERS.register("worldforgemenu", () -> new MenuType<>(WorldForgeMenu::new));
    public static final RegistryObject<MenuType<WorldScannerMenu>> WORLD_SCANNER_MENU = CONTAINERS.register("worldscannermenu", () -> new MenuType<>(WorldScannerMenu::new));

    public static final RegistryObject<SoundEvent> SPAWN_CHUNK_SOUND_EVENT = SOUNDS.register("spawnchunkevent", () -> new SoundEvent(new ResourceLocation(ChunkByChunkConstants.MOD_ID, "chunk_spawn_sound")));

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CONFIG_CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ChunkByChunkConstants.MOD_ID, "configchannel"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    static {
        List<RegistryObject<Block>> triggeredSpawnChunkEntityBlocks = new ArrayList<>();
        triggeredSpawnChunkEntityBlocks.add(TRIGGERED_SPAWN_CHUNK_BLOCK);
        for (ChunkByChunkConstants.BiomeTheme biomeGroup : ChunkByChunkConstants.OVERWORLD_BIOME_THEMES) {
            RegistryObject<Block> spawningBlock = BLOCKS.register(biomeGroup.name() + ChunkByChunkConstants.TRIGGERED_BIOME_CHUNK_BLOCK_SUFFIX, () -> new TriggeredSpawnChunkBlock(ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(ChunkByChunkConstants.MOD_ID, biomeGroup.name() + ChunkByChunkConstants.BIOME_CHUNK_GENERATION_LEVEL_SUFFIX)), BlockBehaviour.Properties.of(Material.AIR)));
            RegistryObject<Block> spawnBlock = BLOCKS.register(biomeGroup.name() + ChunkByChunkConstants.BIOME_CHUNK_BlOCK_SUFFIX, () -> new SpawnChunkBlock(spawningBlock.get(), BlockBehaviour.Properties.of(Material.STONE)));
            ITEMS.register(biomeGroup.name() +  ChunkByChunkConstants.BIOME_CHUNK_BlOCK_ITEM_SUFFIX, () -> new BlockItem(spawnBlock.get(), new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
            triggeredSpawnChunkEntityBlocks.add(spawningBlock);
        }
        TRIGGERED_SPAWN_CHUNK_BLOCK_ENTITY = BLOCK_ENTITIES.register("triggeredspawnchunkentity", () -> BlockEntityType.Builder.of(TriggeredSpawnChunkBlockEntity::new, triggeredSpawnChunkEntityBlocks.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    }

    public ChunkByChunkMod() {
        new ConfigSystem().synchConfig(Paths.get(ChunkByChunkConstants.DEFAULT_CONFIG_PATH, ChunkByChunkConstants.CONFIG_FILE), ChunkByChunkConfig.get());

        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());

        BLOCK_ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        int packetId = 1;
        CONFIG_CHANNEL.registerMessage(packetId++, ConfigMessage.class,
                (configMessage, friendlyByteBuf) -> friendlyByteBuf.writeBoolean(configMessage.blockPlacementAllowed),
                friendlyByteBuf -> new ConfigMessage(friendlyByteBuf.readBoolean()),
                (configMessage, contextSupplier) -> {
                    ChunkByChunkConfig.get().getGameplayConfig().setBlockPlacementAllowedOutsideSpawnedChunks(configMessage.blockPlacementAllowed);
                    contextSupplier.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ChunkByChunkClientMod.registerConfigScreen();
            MenuScreens.register(BEDROCK_CHEST_MENU.get(), BedrockChestScreen::new);
            MenuScreens.register(WORLD_FORGE_MENU.get(), WorldForgeScreen::new);
            MenuScreens.register(WORLD_SCANNER_MENU.get(), WorldScannerScreen::new);
        });
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        Registry.register(Registry.CHUNK_GENERATOR, new ResourceLocation(ChunkByChunkConstants.MOD_ID, "skychunkgenerator"), SkyChunkGenerator.CODEC);
        Registry.register(Registry.CHUNK_GENERATOR, new ResourceLocation(ChunkByChunkConstants.MOD_ID, "netherchunkgenerator"), NetherChunkByChunkGenerator.CODEC);
    }

    @SubscribeEvent
    public void registerResourceReloadListeners(AddReloadListenerEvent e) {
        e.addListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                WorldScannerBlockEntity.clearItemMappings();
                Gson gson = new GsonBuilder().create();
                int count = 0;
                for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(ChunkByChunkConstants.SCANNER_DATA_PATH, r -> true).entrySet()) {
                    try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                        ScannerData data = gson.fromJson(reader, ScannerData.class);
                        data.process(entry.getKey());
                        count++;
                    } catch (IOException |RuntimeException e) {
                        ChunkByChunkConstants.LOGGER.error("Failed to read scanner data '{}'", entry.getKey(), e);
                    }
                }
                ChunkByChunkConstants.LOGGER.info("Loaded {} scanner data configs", count);
            }

            @Override
            public String getName() {
                return ChunkByChunkConstants.MOD_ID + ":scanner_data";
            }
        });
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        SpawnChunkCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlaceItem(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        BlockPos placePos = pos.relative(event.getFace());
        if (!CommonEventHandler.isBlockPlacementAllowed(placePos, event.getEntity(), event.getLevel())) {
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerEventHandler.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarting(ServerAboutToStartEvent event) {
        ServerEventHandler.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        CONFIG_CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)(event.getEntity())), new ConfigMessage(ChunkByChunkConfig.get().getGameplayConfig().isBlockPlacementAllowedOutsideSpawnedChunks()));
    }

    private static class ConfigMessage {
        boolean blockPlacementAllowed;

        public ConfigMessage(boolean blockPlacementAllowed) {
            this.blockPlacementAllowed = blockPlacementAllowed;
        }
    }
}