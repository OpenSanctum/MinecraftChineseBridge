package com.spensanctum.tcb;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

import java.util.ArrayList;

@Mod(NeoForgeEntrypoint.MOD_ID)
public final class NeoForgeEntrypoint {
    static final String MOD_ID = "tchineseb";

    public NeoForgeEntrypoint() {
        if (!isClient()) return;
        GenerationCoordinator.start(FMLPaths.GAMEDIR.get(), request -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null) client.execute(() -> reload(client, request));
        });
    }

    private void reload(Minecraft client, GenerationCoordinator.ReloadRequest request) {
        PackRepository packs = client.getResourcePackRepository();
        ArrayList<String> selected = new ArrayList<>(packs.getSelectedIds());
        packs.reload();
        if (request.selectAtHighestPriority()) {
            selected.removeIf(id -> id.startsWith("file/ChineseBridge-"));
            selected.add(request.packId());
        } else if (selected.stream().noneMatch(id -> id.equals(request.packId()))) {
            selected.add(request.packId());
        }
        packs.setSelected(selected);
        client.reloadResourcePacks();
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
