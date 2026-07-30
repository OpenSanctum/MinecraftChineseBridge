package tw.rainblue.mic.zhtwautopack;

import net.neoforged.fml.common.Mod;

import java.nio.file.Path;

@Mod(NeoForgeEntrypoint.MOD_ID)
public final class NeoForgeEntrypoint {
    static final String MOD_ID = "tchineseb";

    public NeoForgeEntrypoint() {
        if (!isClient()) return;
        try {
            GeneratedPackGenerator.Result result =
                    new GeneratedPackGenerator(Path.of(System.getProperty("user.dir"))).generateAndEnable();
            System.out.printf("[TChineseB] 已產生 %s，共 %d 個語系檔。%n",
                    result.packFile(), result.filesWritten());
        } catch (Exception exception) {
            System.err.println("[TChineseB] 無法產生繁體中文資源包");
            exception.printStackTrace();
        }
    }

    private boolean isClient() {
        try {
            Object dist = Class.forName("net.neoforged.fml.loading.FMLEnvironment")
                    .getField("dist").get(null);
            return "CLIENT".equals(String.valueOf(dist));
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }
}
