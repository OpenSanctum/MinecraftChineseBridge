package tw.rainblue.mic.zhtwautopack;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/** Client entry point. The generated pack is persistent, so it also survives launcher restarts. */
public final class ZhTwAutoPackClient implements ClientModInitializer {
    public static final String MOD_ID = "zhtw_autopack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        GenerationCoordinator.start(client.gameDirectory.toPath(), result -> {
            if (client != null) {
                client.execute(() -> reload(client, result.packId()));
            }
            LOGGER.info("ZH-TW Auto Pack: wrote {} language files from {} sources.",
                    result.filesWritten(), result.sourcesRead());
        }, exception -> LOGGER.error("Could not generate the ZH-TW resource pack", exception));
    }

    private void reload(Minecraft client, String packId) {
        PackRepository packs = client.getResourcePackRepository();
        ArrayList<String> selected = new ArrayList<>(packs.getSelectedIds());
        packs.reload();
        selected.removeIf(id -> id.startsWith("file/ChineseBridge-"));
        selected.add(packId);
        packs.setSelected(selected);
        client.reloadResourcePacks();
    }
}
