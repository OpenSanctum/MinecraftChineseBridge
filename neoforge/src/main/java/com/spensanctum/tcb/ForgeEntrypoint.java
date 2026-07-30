package com.spensanctum.tcb;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.ArrayList;

@Mod(ForgeEntrypoint.MOD_ID)
public final class ForgeEntrypoint {
    static final String MOD_ID = "tchineseb";

    public ForgeEntrypoint() {
        if (!isClient()) return;
        GenerationCoordinator.start(Path.of(System.getProperty("user.dir")), request -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null) client.execute(() -> reload(client, request));
        });
    }

    private void reload(Minecraft client, GenerationCoordinator.ReloadRequest request) {
        PackRepository packs = client.getResourcePackRepository();
        packs.reload();
        if (request.selectAtHighestPriority()) {
            ArrayList<String> selected = new ArrayList<>(packs.getSelectedIds());
            selected.removeIf(id -> id.startsWith("file/TChineseB-"));
            selected.add(request.packId());
            packs.setSelected(selected);
        }
        client.reloadResourcePacks();
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
