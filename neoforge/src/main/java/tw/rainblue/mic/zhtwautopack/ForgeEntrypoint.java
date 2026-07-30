package tw.rainblue.mic.zhtwautopack;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ForgeEntrypoint.MOD_ID)
public final class ForgeEntrypoint {
    static final String MOD_ID = "tchineseb";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static final class ClientEvents {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                Minecraft client = Minecraft.getInstance();
                try {
                    GeneratedPackGenerator.Result result = new GeneratedPackGenerator(client.gameDirectory.toPath()).generate();
                    String id = "file/" + GeneratedPackGenerator.PACK_FILE;
                    client.options.resourcePacks.remove("file/" + GeneratedPackGenerator.PACK_DIRECTORY);
                    if (!client.options.resourcePacks.contains(id)) {
                        client.options.resourcePacks.add(id);
                    }
                    client.options.save();
                    client.reloadResourcePacks();
                    LOGGER.info("Wrote {} Traditional Chinese language files from {} sources", result.filesWritten(), result.sourcesRead());
                } catch (Exception exception) {
                    LOGGER.error("Could not generate Traditional Chinese resource pack", exception);
                }
            });
        }
    }
}
