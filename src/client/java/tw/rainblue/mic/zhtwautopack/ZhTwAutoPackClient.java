package tw.rainblue.mic.zhtwautopack;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry point. The generated pack is persistent, so it also survives launcher restarts. */
public final class ZhTwAutoPackClient implements ClientModInitializer {
    public static final String MOD_ID = "zhtw_autopack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        try {
            GeneratedPackGenerator.Result result = new GeneratedPackGenerator(client.gameDirectory.toPath()).generate();
            String packId = "file/" + GeneratedPackGenerator.PACK_DIRECTORY;
            if (!client.options.resourcePacks.contains(packId)) {
                client.options.resourcePacks.add(packId);
                client.options.save();
                client.reloadResourcePacks();
            }
            LOGGER.info("ZH-TW Auto Pack: wrote {} language files from {} sources.", result.filesWritten(), result.sourcesRead());
        } catch (Exception exception) {
            LOGGER.error("Could not generate the ZH-TW resource pack", exception);
        }
    }
}
