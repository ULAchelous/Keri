package io.ula.keri;

import com.google.gson.JsonObject;
import io.ula.api.config.ConfigFile;
import io.ula.api.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Path;

public class Keri implements ModInitializer {
    public static final String MOD_ID = "keri";
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).get().getMetadata().getVersion().toString();
    public static final Path SERVER_ROOT = FabricLoader.getInstance().getGameDir();
    public static final Path CONFIG_PATH = Path.of(new File(SERVER_ROOT.toString() + "/config/").toURI());
    private static ConfigManager configManager = new ConfigManager(MOD_ID,VERSION,CONFIG_PATH);
    public static ConfigManager getConfigManager(){
        return configManager;
    }
    @Override
    public void onInitialize() {
        JsonObject keiContent = new JsonObject();
        keiContent.addProperty("api","");
        keiContent.addProperty("name","kei");
        keiContent.addProperty("skin","2b856f35-91bb-4a09-80b6-6c81d7d28787");
        Keri.getConfigManager().register("keri:kei",new ConfigFile("kei.json",null,keiContent));
    }
}
