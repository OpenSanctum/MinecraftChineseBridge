package tw.rainblue.mic.zhtwautopack;

import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod(ForgeEntrypoint.MOD_ID)
public final class ForgeEntrypoint {
    static final String MOD_ID = "tchineseb";

    public ForgeEntrypoint() {
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
        for (String environment : new String[]{
                "net.minecraftforge.fml.loading.FMLEnvironment",
                "net.neoforged.fml.loading.FMLEnvironment"}) {
            try {
                Object dist = Class.forName(environment).getField("dist").get(null);
                return "CLIENT".equals(String.valueOf(dist));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return true;
    }
}
