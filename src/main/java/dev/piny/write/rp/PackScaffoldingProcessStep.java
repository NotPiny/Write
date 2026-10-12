package dev.piny.write.rp;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dev.piny.write.Write;
import org.bukkit.NamespacedKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PackScaffoldingProcessStep {
    static class ScaffoldConfig {
        static class ScaffoldBlock {
            enum ModelType {
                @SerializedName("cube_all")
                CUBE_ALL,
                @SerializedName("cube")
                CUBE,
                @SerializedName("none")
                NONE
            }
            ModelType type;
            String key; // NamespacedKey
        }

        public List<ScaffoldBlock> blocks;
        public List<String> items;
    }

    public static void processScaffoldConfig(Path extractedPackRoot) throws IOException {
        Path scaffoldConfig = extractedPackRoot.resolve("wscaffold.json");

        if (Files.exists(scaffoldConfig)) {
            String configContent = Files.readString(scaffoldConfig);
            ScaffoldConfig config = new Gson().fromJson(configContent, ScaffoldConfig.class);
            Write.getInstance().getLogger().info("Scaffold config loaded with " + (config.blocks != null ? config.blocks.size() : 0) + " blocks and " + (config.items != null ? config.items.size() : 0) + " items.");

            if (config.blocks != null) {
                for (ScaffoldConfig.ScaffoldBlock block : config.blocks) {
                    if (block.key == null) {
                        Write.getInstance().getLogger().warning("Skipping block with missing key.");
                        continue;
                    }

                    NamespacedKey blockKey = NamespacedKey.fromString(block.key);
                    if (blockKey == null) {
                        Write.getInstance().getLogger().warning("Invalid block key: " + block.key);
                        continue;
                    }

                    if (block.type == null) {
                        Write.getInstance().getLogger().warning("Block '" + block.key + "' is missing a 'type', defaulting to 'cube_all'.");
                        block.type = ScaffoldConfig.ScaffoldBlock.ModelType.CUBE_ALL;
                    }

                    String itemDef = """
                            {
                                "model": {
                                    "type": "minecraft:model",
                                    "model": "%s:block/%s"
                                }
                            }
                            """.formatted(blockKey.getNamespace(), blockKey.getKey());

                    extractedPackRoot.resolve("assets/" + blockKey.getNamespace() + "/items/block/").toFile().mkdirs();
                    Files.writeString(extractedPackRoot.resolve("assets/" + blockKey.getNamespace() + "/items/block/" + blockKey.getKey() + ".json"), itemDef);

                    String modelContent;

                    if (block.type.equals(ScaffoldConfig.ScaffoldBlock.ModelType.NONE)) {
                        continue; // Skip model generation for NONE type
                    }

                    if (block.type.equals(ScaffoldConfig.ScaffoldBlock.ModelType.CUBE_ALL)) {
                        modelContent = """
                                {
                                    "parent": "minecraft:block/cube_all",
                                    "textures": {
                                        "all": "%s:block/%s"
                                    }
                                }
                                """.formatted(blockKey.getNamespace(), blockKey.getKey());
                    } else {
                        modelContent = """
                                {
                                    "parent": "minecraft:block/cube",
                                    "textures": {
                                        "north": "%s:block/%s_north",
                                        "south": "%s:block/%s_south",
                                        "east": "%s:block/%s_east",
                                        "west": "%s:block/%s_west",
                                        "up": "%s:block/%s_up",
                                        "down": "%s:block/%s_down"
                                    }
                                }
                                """
                        .formatted(
                            blockKey.getNamespace(), blockKey.getKey(),
                            blockKey.getNamespace(), blockKey.getKey(),
                            blockKey.getNamespace(), blockKey.getKey(),
                            blockKey.getNamespace(), blockKey.getKey(),
                            blockKey.getNamespace(), blockKey.getKey(),
                            blockKey.getNamespace(), blockKey.getKey()
                        );
                    }

                    extractedPackRoot.resolve("assets/" + blockKey.getNamespace() + "/models/block/").toFile().mkdirs();
                    Files.writeString(extractedPackRoot.resolve("assets/" + blockKey.getNamespace() + "/models/block/" + blockKey.getKey() + ".json"), modelContent);
                }
            }

            if (config.items != null) {
                for (String itemKeyStr : config.items) {
                    NamespacedKey itemKey = NamespacedKey.fromString(itemKeyStr);
                    if (itemKey == null) {
                        Write.getInstance().getLogger().warning("Invalid item key: " + itemKeyStr);
                        continue;
                    }

                    String itemDef = """
                            {
                                "model": {
                                    "type": "minecraft:model",
                                    "model": "%s:item/%s"
                                }
                            }
                            """.formatted(itemKey.getNamespace(), itemKey.getKey());

                    extractedPackRoot.resolve("assets/" + itemKey.getNamespace() + "/items/").toFile().mkdirs();
                    Files.writeString(extractedPackRoot.resolve("assets/" + itemKey.getNamespace() + "/items/" + itemKey.getKey() + ".json"), itemDef);

                    String modelContent = """
                            {
                                "parent": "minecraft:item/generated",
                                "textures": {
                                    "layer0": "%s:item/%s"
                                }
                            }
                            """.formatted(itemKey.getNamespace(), itemKey.getKey());

                    extractedPackRoot.resolve("assets/" + itemKey.getNamespace() + "/models/item/").toFile().mkdirs();
                    Files.writeString(extractedPackRoot.resolve("assets/" + itemKey.getNamespace() + "/models/item/" + itemKey.getKey() + ".json"), modelContent);
                }
            }
        } else {
            Write.getInstance().getLogger().info("No scaffold config found at: " + scaffoldConfig);
        }
    }
}
