package com.spensanctum.tcb;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;

public final class FabricEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GenerationCoordinator.start(FabricLoader.getInstance().getGameDir(), request -> {
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
}
