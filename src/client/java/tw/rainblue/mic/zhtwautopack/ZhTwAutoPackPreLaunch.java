package tw.rainblue.mic.zhtwautopack;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class ZhTwAutoPackPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        try {
            SharedGeneratedPackGenerator.Result result =
                    new SharedGeneratedPackGenerator(FabricLoader.getInstance().getGameDir()).generateAndEnable();
            System.out.printf("[ChineseBridge] 已產生 %s，共 %d 個語系檔。%n",
                    result.packFile(), result.filesWritten());
        } catch (Exception exception) {
            System.err.println("[ChineseBridge] 無法產生繁體中文資源包");
            exception.printStackTrace();
        }
    }
}
