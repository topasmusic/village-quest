param(
    [string]$Target = "C:\Users\me\Desktop\Topas Mods\Wolkensprung"
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot

function Ensure-Directory {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

function Write-Utf8 {
    param(
        [string]$Path,
        [string]$Content
    )

    $parent = Split-Path -Parent $Path
    Ensure-Directory $parent
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Copy-TextWithReplacements {
    param(
        [string]$SourcePath,
        [string]$DestinationPath,
        [hashtable]$Replacements
    )

    $content = Get-Content -Path $SourcePath -Raw
    foreach ($entry in $Replacements.GetEnumerator()) {
        $content = $content.Replace([string]$entry.Key, [string]$entry.Value)
    }
    Write-Utf8 -Path $DestinationPath -Content $content
}

Ensure-Directory $Target
Ensure-Directory (Join-Path $Target "gradle\wrapper")
Ensure-Directory (Join-Path $Target "src\main\resources\assets\wolkensprung")

Copy-Item -Path (Join-Path $RepoRoot ".gitignore") -Destination (Join-Path $Target ".gitignore") -Force
Copy-Item -Path (Join-Path $RepoRoot "gradlew") -Destination (Join-Path $Target "gradlew") -Force
Copy-Item -Path (Join-Path $RepoRoot "gradlew.bat") -Destination (Join-Path $Target "gradlew.bat") -Force
Copy-Item -Path (Join-Path $RepoRoot "LICENSE") -Destination (Join-Path $Target "LICENSE") -Force
Copy-Item -Path (Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.jar") -Destination (Join-Path $Target "gradle\wrapper\gradle-wrapper.jar") -Force
Copy-Item -Path (Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.properties") -Destination (Join-Path $Target "gradle\wrapper\gradle-wrapper.properties") -Force

Copy-Item -Path (Join-Path $RepoRoot "src\main\resources\assets\village-quest\icon.png") -Destination (Join-Path $Target "src\main\resources\assets\wolkensprung\icon.png") -Force

Write-Utf8 -Path (Join-Path $Target "settings.gradle") -Content @'
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Wolkensprung"
'@

Write-Utf8 -Path (Join-Path $Target "build.gradle") -Content @'
plugins {
    id 'fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
    maven { url "https://maven.fabricmc.net/" }
}

loom {
    splitEnvironmentSourceSets()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"

    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") { expand "version": inputs.properties.version }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 21
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.register("pruneOldBuildLibs") {
    doLast {
        def libsDir = file("$buildDir/libs")
        if (!libsDir.exists()) {
            return
        }

        def currentPrefix = "${project.archives_base_name}-${project.version}"
        fileTree(libsDir) {
            include "${project.archives_base_name}-*.jar"
        }.files.findAll { !it.name.startsWith(currentPrefix) }.each { file ->
            if (!file.delete()) {
                throw new GradleException("Could not delete old build artifact: ${file.name}")
            }
        }
    }
}

tasks.named("build").configure {
    finalizedBy(tasks.named("pruneOldBuildLibs"))
}

jar {
    inputs.property "archivesName", project.base.archivesName
    from("LICENSE") { rename { "${it}_${inputs.properties.archivesName}" } }
}

publishing {
    publications {
        create("mavenJava", MavenPublication) {
            artifactId = project.archives_base_name
            from components.java
        }
    }
}
'@

Write-Utf8 -Path (Join-Path $Target "gradle.properties") -Content @'
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=false

minecraft_version=1.21.11
yarn_mappings=1.21.11+build.4
loader_version=0.18.4
loom_version=1.15.5

mod_version=0.1.0
maven_group=de.wolkensprung
archives_base_name=wolkensprung

fabric_version=0.141.3+1.21.11
'@

Write-Utf8 -Path (Join-Path $Target "README.md") -Content @'
# Wolkensprung

Standalone extraction batch from `Village Quest`.

Current scope:

- Wolkensprung core quest logic
- Wolkensprung NPC entity and renderer
- `/wolkensprung` admin commands
- `/quest accept` and `/quest decline` for the Wolkensprung offer flow
- standalone persistent state for chest, checkpoint, respawn, and completion data

Still intentionally left in `Village Quest` for later batches:

- journal integration
- daily quest integration
- NPC config screen networking
- actual removal of Wolkensprung code from the `Village Quest` jar

Build:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'; .\gradlew.bat build
```
'@

Write-Utf8 -Path (Join-Path $Target "src\main\resources\fabric.mod.json") -Content @'
{
  "schemaVersion": 1,
  "id": "wolkensprung",
  "version": "${version}",
  "name": "Wolkensprung",
  "description": "Standalone Wolkensprung story quest with quest NPC, chest run, checkpoints, and admin tools.",
  "authors": [
    "Me!"
  ],
  "contact": {
    "homepage": "https://fabricmc.net/",
    "sources": "https://github.com/FabricMC/fabric-example-mod"
  },
  "license": "CC0-1.0",
  "icon": "assets/wolkensprung/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": [
      "de.wolkensprung.WolkensprungMod"
    ],
    "client": [
      "de.wolkensprung.client.WolkensprungClient"
    ]
  },
  "depends": {
    "fabricloader": ">=0.18.4",
    "minecraft": "~1.21.11",
    "java": ">=21",
    "fabric-api": ">=0.141.3"
  }
}
'@

Write-Utf8 -Path (Join-Path $Target "src\main\resources\assets\wolkensprung\lang\de_de.json") -Content @'
{
  "entity.wolkensprung.wolkensprung_npc": "Wolkensprung-NPC",
  "tooltip.wolkensprung.token": "Holzkiste",
  "quest.wolkensprung.token": "[Koffer]",
  "quest.wolkensprung.active.prefix": "Finde einen Weg zur Insel und bring mir meinen ",
  "quest.wolkensprung.active.suffix": " wieder.",
  "quest.wolkensprung.chest_hint.prefix": "Zaudre nicht! Geh und bring mir meinen ",
  "quest.wolkensprung.chest_hint.suffix": " heim."
}
'@

Write-Utf8 -Path (Join-Path $Target "src\main\resources\assets\wolkensprung\lang\en_us.json") -Content @'
{
  "entity.wolkensprung.wolkensprung_npc": "Wolkensprung NPC",
  "tooltip.wolkensprung.token": "Wooden Chest",
  "quest.wolkensprung.token": "[Case]",
  "quest.wolkensprung.active.prefix": "Find a way to the island and bring back my ",
  "quest.wolkensprung.active.suffix": ".",
  "quest.wolkensprung.chest_hint.prefix": "Don't dawdle! Go and bring my ",
  "quest.wolkensprung.chest_hint.suffix": " home."
}
'@

Write-Utf8 -Path (Join-Path $Target "src\main\java\de\wolkensprung\WolkensprungMod.java") -Content @'
package de.wolkensprung;

import de.wolkensprung.command.WolkensprungCommands;
import de.wolkensprung.quest.WolkensprungService;
import de.wolkensprung.registry.ModEntities;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WolkensprungMod implements ModInitializer {
    public static final String MOD_ID = "wolkensprung";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);
        ModEntities.register();
        WolkensprungService.registerEvents();
        WolkensprungCommands.register();
    }
}
'@

Write-Utf8 -Path (Join-Path $Target "src\client\java\de\wolkensprung\client\WolkensprungClient.java") -Content @'
package de.wolkensprung.client;

import de.wolkensprung.client.render.QuestGiverEntityRenderer;
import de.wolkensprung.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

public final class WolkensprungClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(
                QuestGiverEntityRenderer.WOLKENSPRUNG_NPC_LAYER,
                () -> TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, true), 64, 64)
        );
        EntityRendererRegistry.register(ModEntities.WOLKENSPRUNG_NPC, QuestGiverEntityRenderer::new);
    }
}
'@

Write-Utf8 -Path (Join-Path $Target "src\main\java\de\wolkensprung\data\WolkensprungState.java") -Content @'
package de.wolkensprung.data;

import de.wolkensprung.quest.WolkensprungQuest;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class WolkensprungState extends PersistentState {
    private static final String ID = "wolkensprung_state";
    public static final PersistentStateType<WolkensprungState> TYPE =
            new PersistentStateType<>(ID, WolkensprungState::new, NbtCompound.CODEC.xmap(WolkensprungState::fromNbt, WolkensprungState::toNbt), DataFixTypes.LEVEL);

    private NbtCompound runtimeData = new NbtCompound();

    private WolkensprungState() {}

    private static WolkensprungState fromNbt(NbtCompound nbt) {
        WolkensprungState state = new WolkensprungState();
        if (nbt != null) {
            state.runtimeData = nbt.getCompoundOrEmpty("runtime");
        }
        return state;
    }

    private static NbtCompound toNbt(WolkensprungState state) {
        NbtCompound root = new NbtCompound();
        root.put("runtime", WolkensprungQuest.writeToNbt());
        return root;
    }

    public static WolkensprungState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public void updateFromRuntime() {
        markDirty();
    }

    public void applyToRuntime() {
        WolkensprungQuest.readFromNbt(runtimeData);
    }
}
'@

Write-Utf8 -Path (Join-Path $Target "src\main\java\de\wolkensprung\quest\WolkensprungService.java") -Content @'
package de.wolkensprung.quest;

import de.wolkensprung.data.WolkensprungState;
import de.wolkensprung.entity.QuestGiverEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

public final class WolkensprungService {
    private WolkensprungService() {}

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WolkensprungState.get(server).applyToRuntime();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WolkensprungState state = WolkensprungState.get(server);
            state.updateFromRuntime();
            server.getOverworld().getPersistentStateManager().save();
        });

        ServerTickEvents.END_SERVER_TICK.register(WolkensprungQuest::onServerTick);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof QuestGiverEntity));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                return WolkensprungQuest.onBlockUse(sw, sp, hand, hit);
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world instanceof ServerWorld sw && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                if (entity instanceof QuestGiverEntity) {
                    return ActionResult.PASS;
                }
                return WolkensprungQuest.onVillagerInteract(sw, sp, hand, entity);
            }
            return ActionResult.PASS;
        });
    }
}
'@

Write-Utf8 -Path (Join-Path $Target "src\main\java\de\wolkensprung\command\WolkensprungCommands.java") -Content @'
package de.wolkensprung.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.wolkensprung.entity.QuestGiverEntity;
import de.wolkensprung.quest.WolkensprungQuest;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class WolkensprungCommands {
    private static final double NPC_SEARCH_RADIUS = 3.0;

    private WolkensprungCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("wolkensprung")
                    .then(literal("cancel")
                            .executes(ctx -> cancelActive(ctx.getSource())))
                    .then(literal("respawn")
                            .requires(WolkensprungCommands::canManage)
                            .then(literal("set").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                ServerWorld world = ctx.getSource().getWorld();
                                BlockPos pos = player.getBlockPos();
                                WolkensprungQuest.setGlobalRespawn(world, pos);
                                player.sendMessage(Text.literal("Respawn gesetzt auf: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.GRAY), false);
                                return 1;
                            }).then(argument("x", IntegerArgumentType.integer())
                                    .then(argument("y", IntegerArgumentType.integer())
                                            .then(argument("z", IntegerArgumentType.integer())
                                                    .executes(ctx -> {
                                                        ServerWorld world = ctx.getSource().getWorld();
                                                        int x = IntegerArgumentType.getInteger(ctx, "x");
                                                        int y = IntegerArgumentType.getInteger(ctx, "y");
                                                        int z = IntegerArgumentType.getInteger(ctx, "z");
                                                        BlockPos pos = new BlockPos(x, y, z);
                                                        WolkensprungQuest.setGlobalRespawn(world, pos);
                                                        ctx.getSource().sendFeedback(() -> Text.literal("Respawn gesetzt auf: " + x + " " + y + " " + z).formatted(Formatting.GRAY), false);
                                                        return 1;
                                                    })))))
                            .then(literal("clear").executes(ctx -> {
                                WolkensprungQuest.clearGlobalRespawn(ctx.getSource().getWorld());
                                ctx.getSource().sendFeedback(() -> Text.literal("Respawn geloescht.").formatted(Formatting.GRAY), false);
                                return 1;
                            }))
                            .then(literal("height")
                                    .then(argument("y", IntegerArgumentType.integer())
                                            .executes(ctx -> {
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                WolkensprungQuest.setFallYThreshold(ctx.getSource().getWorld(), y);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Fall-Hoehe gesetzt auf: " + y).formatted(Formatting.GRAY), false);
                                                return 1;
                                            })))
                            .then(literal("info").executes(ctx -> {
                                BlockPos pos = WolkensprungQuest.getGlobalRespawnPos();
                                int y = WolkensprungQuest.getFallYThreshold();
                                if (pos == null || WolkensprungQuest.getGlobalRespawnDim() == null) {
                                    ctx.getSource().sendFeedback(() -> Text.literal("Respawn nicht gesetzt.").formatted(Formatting.GRAY), false);
                                } else {
                                    ctx.getSource().sendFeedback(() -> Text.literal("Respawn: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.GRAY), false);
                                }
                                if (y == Integer.MIN_VALUE) {
                                    ctx.getSource().sendFeedback(() -> Text.literal("Fall-Hoehe nicht gesetzt.").formatted(Formatting.GRAY), false);
                                } else {
                                    ctx.getSource().sendFeedback(() -> Text.literal("Fall-Hoehe: " + y).formatted(Formatting.GRAY), false);
                                }
                                return 1;
                            })))
                    .then(literal("area")
                            .requires(WolkensprungCommands::canManage)
                            .then(literal("wand").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                boolean enabled = WolkensprungQuest.toggleWand(player.getUuid());
                                player.sendMessage(Text.literal(enabled
                                        ? "Area-Wand aktiv: Klicke mit einem Stick auf Bloecke, um Spawnpunkte hinzuzufuegen."
                                        : "Area-Wand deaktiviert.").formatted(Formatting.GRAY), false);
                                return 1;
                            }))
                            .then(literal("add").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                ServerWorld world = ctx.getSource().getWorld();
                                BlockPos pos = player.getBlockPos();
                                boolean added = WolkensprungQuest.addCustomSpawnChecked(world, player, pos);
                                if (added) {
                                    int count = WolkensprungQuest.getCustomSpawnCount(world);
                                    player.sendMessage(Text.literal("Position hinzugefuegt. Gesamt: " + count).formatted(Formatting.GRAY), false);
                                }
                                return 1;
                            }))
                            .then(literal("removeall").executes(ctx -> {
                                ServerWorld world = ctx.getSource().getWorld();
                                int removed = WolkensprungQuest.clearCustomSpawns(world);
                                ctx.getSource().sendFeedback(() -> Text.literal("Custom-Spawnliste geleert (" + removed + " Eintraege).").formatted(Formatting.GRAY), false);
                                return 1;
                            }))
                            .then(literal("list").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                WolkensprungQuest.openSpawnListScreen(ctx.getSource().getWorld(), player);
                                return 1;
                            }))
                            .then(literal("set")
                                    .then(argument("x", IntegerArgumentType.integer())
                                            .then(argument("y", IntegerArgumentType.integer())
                                                    .then(argument("z", IntegerArgumentType.integer())
                                                            .executes(ctx -> {
                                                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                                if (player == null) {
                                                                    return 0;
                                                                }
                                                                ServerWorld world = ctx.getSource().getWorld();
                                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                boolean added = WolkensprungQuest.addCustomSpawnChecked(world, player, new BlockPos(x, y, z));
                                                                if (added) {
                                                                    int count = WolkensprungQuest.getCustomSpawnCount(world);
                                                                    player.sendMessage(Text.literal("Position gesetzt. Gesamt: " + count).formatted(Formatting.GRAY), false);
                                                                }
                                                                return 1;
                                                            })))))
                            .then(literal("sethere").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                ServerWorld world = ctx.getSource().getWorld();
                                BlockPos pos = player.getBlockPos().down();
                                boolean added = WolkensprungQuest.addCustomSpawnChecked(world, player, pos);
                                if (added) {
                                    int count = WolkensprungQuest.getCustomSpawnCount(world);
                                    player.sendMessage(Text.literal("Position (aktuell) gesetzt. Gesamt: " + count).formatted(Formatting.GRAY), false);
                                }
                                return 1;
                            }))
                            .then(literal("remove")
                                    .then(argument("x", IntegerArgumentType.integer())
                                            .then(argument("y", IntegerArgumentType.integer())
                                                    .then(argument("z", IntegerArgumentType.integer())
                                                            .executes(ctx -> {
                                                                ServerWorld world = ctx.getSource().getWorld();
                                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                boolean removed = WolkensprungQuest.removeCustomSpawn(world, new BlockPos(x, y, z));
                                                                if (removed) {
                                                                    int count = WolkensprungQuest.getCustomSpawnCount(world);
                                                                    ctx.getSource().sendFeedback(() -> Text.literal("Spawnpunkt entfernt. Verbleibend: " + count).formatted(Formatting.GRAY), false);
                                                                } else {
                                                                    ctx.getSource().sendFeedback(() -> Text.literal("Spawnpunkt nicht gefunden.").formatted(Formatting.RED), false);
                                                                }
                                                                return 1;
                                                            })))))
                    .then(literal("checkpoint")
                            .requires(WolkensprungCommands::canManage)
                            .then(literal("wand").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                boolean enabled = WolkensprungQuest.toggleCheckpointWand(player.getUuid());
                                player.sendMessage(Text.literal(enabled
                                        ? "Checkpoint-Wand aktiv: Klicke mit einem Stick auf Bloecke, um Checkpoints zu setzen."
                                        : "Checkpoint-Wand deaktiviert.").formatted(Formatting.GRAY), false);
                                return 1;
                            }))
                            .then(literal("list").executes(ctx -> {
                                List<BlockPos> checkpoints = WolkensprungQuest.getCheckpoints(ctx.getSource().getWorld());
                                if (checkpoints.isEmpty()) {
                                    ctx.getSource().sendFeedback(() -> Text.literal("Keine Checkpoints gesetzt.").formatted(Formatting.GRAY), false);
                                    return 1;
                                }
                                ctx.getSource().sendFeedback(() -> Text.literal("Checkpoints (" + checkpoints.size() + "):").formatted(Formatting.GRAY), false);
                                for (BlockPos pos : checkpoints) {
                                    ctx.getSource().sendFeedback(() -> Text.literal(" - " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.GRAY), false);
                                }
                                return 1;
                            }))
                            .then(literal("set")
                                    .then(argument("x", IntegerArgumentType.integer())
                                            .then(argument("y", IntegerArgumentType.integer())
                                                    .then(argument("z", IntegerArgumentType.integer())
                                                            .executes(ctx -> {
                                                                ServerWorld world = ctx.getSource().getWorld();
                                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                boolean added = WolkensprungQuest.addCheckpointManual(world, new BlockPos(x, y, z));
                                                                ctx.getSource().sendFeedback(() -> Text.literal(added
                                                                                ? "Checkpoint gesetzt."
                                                                                : "Checkpoint existiert bereits.")
                                                                        .formatted(Formatting.GRAY), false);
                                                                return 1;
                                                            })))))
                            .then(literal("removeall").executes(ctx -> {
                                int removed = WolkensprungQuest.clearCheckpoints(ctx.getSource().getWorld());
                                ctx.getSource().sendFeedback(() -> Text.literal("Checkpoints entfernt (" + removed + ").").formatted(Formatting.GRAY), false);
                                return 1;
                            }))
                            .then(literal("remove")
                                    .then(argument("x", IntegerArgumentType.integer())
                                            .then(argument("y", IntegerArgumentType.integer())
                                                    .then(argument("z", IntegerArgumentType.integer())
                                                            .executes(ctx -> {
                                                                ServerWorld world = ctx.getSource().getWorld();
                                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                boolean removed = WolkensprungQuest.removeCheckpoint(world, new BlockPos(x, y, z));
                                                                ctx.getSource().sendFeedback(() -> Text.literal(removed
                                                                                ? "Checkpoint entfernt."
                                                                                : "Checkpoint nicht gefunden.")
                                                                        .formatted(Formatting.GRAY), false);
                                                                return 1;
                                                            }))))));

            dispatcher.register(literal("npcconfig")
                    .requires(WolkensprungCommands::canManage)
                    .executes(ctx -> showNpcConfig(ctx.getSource()))
                    .then(argument("radius", IntegerArgumentType.integer(0, 64))
                            .executes(ctx -> setNpcRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));

            dispatcher.register(literal("npcdelete")
                    .requires(WolkensprungCommands::canManage)
                    .executes(ctx -> deleteNearestNpc(ctx.getSource())));

            dispatcher.register(literal("npcdeleteid")
                    .requires(WolkensprungCommands::canManage)
                    .then(argument("uuid", StringArgumentType.word())
                            .executes(ctx -> deleteNpcById(ctx.getSource(), StringArgumentType.getString(ctx, "uuid")))));

            dispatcher.register(literal("quest")
                    .then(literal("accept").executes(ctx -> acceptQuest(ctx.getSource())))
                    .then(literal("decline").executes(ctx -> declineQuest(ctx.getSource()))));
        });
    }

    public static boolean canManage(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
    }

    private static int acceptQuest(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        ServerWorld world = source.getWorld();
        WolkensprungQuest.OfferType offer = WolkensprungQuest.consumeOffer(player.getUuid());
        if (offer == null) {
            player.sendMessage(Text.literal("Kein offenes Wolkensprung-Angebot.").formatted(Formatting.RED), false);
            return 0;
        }
        if (offer == WolkensprungQuest.OfferType.TIMER) {
            WolkensprungQuest.acceptTimer(world, player);
        } else {
            WolkensprungQuest.accept(world, player);
        }
        return 1;
    }

    private static int declineQuest(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        WolkensprungQuest.OfferType offer = WolkensprungQuest.consumeOffer(player.getUuid());
        if (offer == null) {
            player.sendMessage(Text.literal("Kein offenes Wolkensprung-Angebot.").formatted(Formatting.RED), false);
            return 0;
        }
        if (offer == WolkensprungQuest.OfferType.TIMER) {
            WolkensprungQuest.declineTimer(player);
        } else {
            WolkensprungQuest.declineOffer(player);
        }
        return 1;
    }

    private static int cancelActive(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        boolean cancelled = WolkensprungQuest.cancel(source.getWorld(), player);
        if (!cancelled) {
            player.sendMessage(Text.literal("Keine aktive Wolkensprung-Quest.").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("Wolkensprung abgebrochen.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int showNpcConfig(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        QuestGiverEntity npc = findNearestNpc(source.getWorld(), player);
        if (npc == null) {
            player.sendMessage(Text.literal("Kein Quest-NPC im 3-Block-Radius.").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("NPC-Radius: " + npc.getWanderRadius() + " Bloecke.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int setNpcRadius(ServerCommandSource source, int radius) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        QuestGiverEntity npc = findNearestNpc(source.getWorld(), player);
        if (npc == null) {
            player.sendMessage(Text.literal("Kein Quest-NPC im 3-Block-Radius.").formatted(Formatting.RED), false);
            return 0;
        }
        npc.setWanderRadius(radius);
        player.sendMessage(Text.literal("NPC-Radius gesetzt auf " + radius + " Bloecke.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int deleteNearestNpc(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        QuestGiverEntity npc = findNearestNpc(source.getWorld(), player);
        if (npc == null) {
            player.sendMessage(Text.literal("Kein Quest-NPC im 3-Block-Radius.").formatted(Formatting.RED), false);
            return 0;
        }
        npc.discard();
        player.sendMessage(Text.literal("Quest-NPC entfernt.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int deleteNpcById(ServerCommandSource source, String rawUuid) {
        UUID id;
        try {
            id = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            source.sendFeedback(() -> Text.literal("Ungueltige NPC-ID.").formatted(Formatting.RED), false);
            return 0;
        }

        for (ServerWorld world : source.getServer().getWorlds()) {
            if (world.getEntity(id) instanceof QuestGiverEntity npc) {
                npc.discard();
                source.sendFeedback(() -> Text.literal("Quest-NPC entfernt.").formatted(Formatting.GRAY), false);
                return 1;
            }
        }

        source.sendFeedback(() -> Text.literal("Kein Quest-NPC mit dieser ID gefunden.").formatted(Formatting.RED), false);
        return 0;
    }

    private static QuestGiverEntity findNearestNpc(ServerWorld world, ServerPlayerEntity player) {
        Box range = player.getBoundingBox().expand(NPC_SEARCH_RADIUS);
        return world.getEntitiesByClass(QuestGiverEntity.class, range, entity -> true)
                .stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
                .orElse(null);
    }
}
'@

Copy-Item -Path (Join-Path $RepoRoot "templates\wolkensprung\src\main\java\de\wolkensprung\command\WolkensprungCommands.java") -Destination (Join-Path $Target "src\main\java\de\wolkensprung\command\WolkensprungCommands.java") -Force

Copy-TextWithReplacements -SourcePath (Join-Path $RepoRoot "src\main\java\de\quest\entity\QuestGiverEntity.java") -DestinationPath (Join-Path $Target "src\main\java\de\wolkensprung\entity\QuestGiverEntity.java") -Replacements ([ordered]@{
    "package de.quest.entity;" = "package de.wolkensprung.entity;"
    "import de.quest.wolkensprung.WolkensprungModule;" = "import de.wolkensprung.quest.WolkensprungQuest;"
    "spawn via /summon village-quest:wolkensprung_npc." = "spawn via /summon wolkensprung:wolkensprung_npc."
    "ActionResult result = WolkensprungModule.onQuestGiverInteract(serverWorld, serverPlayer, hand, this);" = "ActionResult result = WolkensprungQuest.onVillagerInteract(serverWorld, serverPlayer, hand, this);"
})

Copy-TextWithReplacements -SourcePath (Join-Path $RepoRoot "src\main\java\de\quest\registry\ModEntities.java") -DestinationPath (Join-Path $Target "src\main\java\de\wolkensprung\registry\ModEntities.java") -Replacements ([ordered]@{
    "package de.quest.registry;" = "package de.wolkensprung.registry;"
    "import de.quest.VillageQuest;" = "import de.wolkensprung.WolkensprungMod;"
    "import de.quest.entity.QuestGiverEntity;" = "import de.wolkensprung.entity.QuestGiverEntity;"
    "Identifier.of(VillageQuest.MOD_ID, ""wolkensprung_npc"")" = "Identifier.of(WolkensprungMod.MOD_ID, ""wolkensprung_npc"")"
    "VillageQuest.LOGGER.info(""Registered entities"");" = "WolkensprungMod.LOGGER.info(""Registered entities"");"
})

Copy-TextWithReplacements -SourcePath (Join-Path $RepoRoot "src\client\java\de\quest\client\render\QuestGiverEntityRenderer.java") -DestinationPath (Join-Path $Target "src\client\java\de\wolkensprung\client\render\QuestGiverEntityRenderer.java") -Replacements ([ordered]@{
    "package de.quest.client.render;" = "package de.wolkensprung.client.render;"
    "import de.quest.entity.QuestGiverEntity;" = "import de.wolkensprung.entity.QuestGiverEntity;"
    'Identifier.of("village-quest", "wolkensprung_npc")' = 'Identifier.of("wolkensprung", "wolkensprung_npc")'
})

Copy-TextWithReplacements -SourcePath (Join-Path $RepoRoot "src\main\java\de\quest\content\story\WolkensprungQuest.java") -DestinationPath (Join-Path $Target "src\main\java\de\wolkensprung\quest\WolkensprungQuest.java") -Replacements ([ordered]@{
    "package de.quest.content.story;" = "package de.wolkensprung.quest;"
    "import de.quest.VillageQuest;" = ""
    "import de.quest.data.QuestState;" = "import de.wolkensprung.data.WolkensprungState;"
    "import de.quest.entity.QuestGiverEntity;" = "import de.wolkensprung.entity.QuestGiverEntity;"
    "QuestState.get(world.getServer()).updateFromRuntime();" = "WolkensprungState.get(world.getServer()).updateFromRuntime();"
    "quest.village-quest.wolkensprung" = "quest.wolkensprung"
    "tooltip.village-quest.wolken_token" = "tooltip.wolkensprung.token"
})

Write-Output ("Generated standalone Wolkensprung project at: " + $Target)
