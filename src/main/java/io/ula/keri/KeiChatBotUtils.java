package io.ula.keri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.ula.api.config.ConfigFile;
import io.ula.drng.Main;
import io.ula.drng.utils.PlayerUtils;
import io.ula.drng.utils.Util;
import io.ula.drng.utils.kei.tool.FuncTool;
import io.ula.drng.utils.kei.tool.Tool;
import io.ula.drng.utils.kei.tool.Tools;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;



public class KeiChatBotUtils {
    private static record Message(String msg,UUID user){};
    private static final int MAX_TOOL_ROUNDS = 4;
    private static String prompt = "";
    public static Map<UUID,Boolean> map = new HashMap<>();
    private static Queue<String> msgQueue = new LinkedList<>();
    private static Queue<Message> queuedMsg = new LinkedList<>();
    private static MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();
    private static ConfigFile keiConfig;
    private static Logger LOGGER = LogManager.getLogger("dr-ng/kei");
    private static volatile Boolean isGenerating = false;
    public static void onChat(String msg, UUID user){
        if(!isGenerating)
            CompletableFuture.runAsync(() -> callModel(user,msg));
        else
            queuedMsg.offer(new Message(msg,user));
    }
    public static void callModel(UUID user,String msg){
        isGenerating = true;
        Boolean usePuca = keiConfig.getKey("use_puca_bot").getAsBoolean();
        try {
            URL baseURL;
            try {
                // puca.api 为基础路径（如 http://127.0.0.1:8700/v1）→ 拼完整 chat 端点；
                // 若已带 /chat/completions 后缀则直接用（兼容两种写法）
                String apiUrl = !usePuca ? keiConfig.getKey("api").getAsString()
                        : keiConfig.getKey("puca").getAsJsonObject().get("api").getAsString();
                if(usePuca && !apiUrl.endsWith("/chat/completions"))
                    apiUrl = (apiUrl.endsWith("/") ? apiUrl : apiUrl + "/") + "chat/completions";
                baseURL = new URL(apiUrl);
            }catch (MalformedURLException e){
                LOGGER.error(e.getMessage());
                LogErr("apiURL格式不正确");
                return;
            }
            String apiKey,model,systemPrompt="";
            if(!usePuca) {
                apiKey = keiConfig.getKey("api_key").getAsString();
                model = keiConfig.getKey("model").getAsString();
                systemPrompt = keiConfig.getKey("system_prompt").getAsString() + prompt;

            }else {
                JsonObject pucaConfig = keiConfig.getKey("puca").getAsJsonObject();
                apiKey = pucaConfig.get("api_key").getAsString()+"/chat/completions";
                model = pucaConfig.get("model").getAsString();
            }
            if (apiKey.isBlank()) {
                LogErr("apiKey为空");
                return;
            }
            if (model.isBlank()) {
                LogErr("模型名称为空");
                return;
            }
            ServerPlayer sender = null;
            if(server != null)
               sender = server.getPlayerList().getPlayer(user);
            JsonArray messages = new JsonArray();
            if(!usePuca) {
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", systemPrompt);
                messages.add(systemMsg);
            }
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role","user");
            userMsg.addProperty("content",!usePuca && sender != null ? buildChatMsg(msg,sender) : msg);
            messages.add(userMsg);

            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            payload.add("messages", messages);
            payload.add("tools", buildTools());

            //puca bot兼容
            JsonArray toolRounds = null;
            if(usePuca){
                payload.addProperty("conversation_id","mc-desideraregio");
                payload.addProperty("sender_id",user.toString());
                payload.addProperty("sender_name",sender != null ? sender.getName().getString() : "unknown");
                JsonArray cache = new JsonArray();
                while (!msgQueue.isEmpty())
                    cache.add(msgQueue.poll());
                payload.add("cache",cache);
                toolRounds = new JsonArray();
                payload.add("tool_rounds",toolRounds);
            }

            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                JsonObject response;
                if(usePuca)
                    payload.addProperty("tool_round", round);
                if(round == 1)
                    payload.remove("cache");
                try {
                    response = Util.requestOpenAIAPIAsJson(baseURL, payload, 10000, 60000, apiKey);
                } catch (Exception e){
                    LOGGER.error("请求模型 API 失败", e);
                    LogErr("请求模型 API 失败，详见服务端日志");
                    return;
                }
                if (response.has("http_code")) {
                    String errorInfo = response.has("error") ? response.get("error").toString() : "未知错误";
                    LOGGER.error("模型 API 返回错误码 {}: {}", response.get("http_code").getAsInt(), errorInfo);
                    LogErr("模型 API 返回错误，详见服务端日志");
                    return;
                }
                JsonArray choices = response.getAsJsonArray("choices");
                if (choices == null || choices.size() == 0) {
                    LogErr("API 响应异常：未包含 choices");
                    return;
                }
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (!message.has("tool_calls") || message.getAsJsonArray("tool_calls").size() == 0) {
                    String content = message.has("content") && !message.get("content").isJsonNull()
                            ? message.get("content").getAsString() : "";
                    sendReply(content,server.getPlayerList().getPlayer(user));
                    return;
                }
                if(usePuca)
                    toolRounds.add(message);
                else
                    messages.add(message);
                for (JsonElement toolCallElement : message.getAsJsonArray("tool_calls")) {
                    JsonObject toolCall = toolCallElement.getAsJsonObject();
                    String toolName = toolCall.getAsJsonObject("function").get("name").getAsString();

                    JsonObject args = new JsonObject();
                    JsonObject fn = toolCall.getAsJsonObject("function");
                    if(fn.has("arguments") && !fn.get("arguments").isJsonNull()){
                        String argsStr = fn.get("arguments").getAsString();
                        if(!argsStr.isBlank()){
                            try {
                                args = JsonParser.parseString(argsStr).getAsJsonObject();
                            }catch (Exception e){
                                LOGGER.error("工具参数解析失败: {}", argsStr, e);
                            }
                        }
                    }
                    JsonObject toolResult = executeTool(toolName, args);
                    JsonObject toolMsg = new JsonObject();
                    toolMsg.addProperty("role","tool");
                    toolMsg.addProperty("tool_call_id", toolCall.get("id").getAsString());
                    toolMsg.addProperty("content", toolResult.toString());
                    if(usePuca)
                        toolRounds.add(toolMsg);
                    else
                        messages.add(toolMsg);
                }
            }
            LogErr("工具调用轮数超限，已停止");
        } finally {
            isGenerating = false;
        }
        Message next = queuedMsg.poll();
        if(next != null){
            CompletableFuture.runAsync(() -> callModel(next.user(),next.msg()));
        }
    }

    private static String buildChatMsg(String content, Player sender){
        String result;
        String playerInfo = sender.getName().getString() + "(" + sender.getUUID().toString() + ")";
        ZonedDateTime zdt = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(" E yyyy-MM-dd HH:mm:ss ",Locale.ENGLISH);
        int hourOffSet = zdt.getOffset().getTotalSeconds() / 3600;
        String zoneOffSet = "GMT" + (hourOffSet >= 0?"+":"") + Integer.toString(hourOffSet);
        String dateTimeStr = zdt.format(formatter)+zoneOffSet;
        result ="[" + playerInfo + dateTimeStr + "] " + content;
        return result;
    }

    private static JsonObject executeTool(String name, JsonObject args){
        for(Tool t : Tools.TOOLS){
            if(t instanceof FuncTool tool) {
                if (!tool.getName().equals(name))
                    continue;

                MinecraftServer srv = server;
                if (srv == null) return errorJson("server 不可用");
                if (srv.isSameThread()) return tool.func(args);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<JsonObject> result = new AtomicReference<>();
                srv.execute(() -> {
                    try {
                        result.set(tool.func(args));
                    } catch (Exception e) {
                        LOGGER.error("工具执行异常", e);
                        result.set(errorJson("工具执行异常"));
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return result.get();
            }
        }
        return errorJson("未知工具: " + name);
    }


    public static ServerPlayer getPlayerByUuid(String uuid){
        if(server == null || uuid == null || uuid.isBlank())
            return null;
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (Exception e){
            return null;
        }
    }

    public static void clearContext(){
        msgQueue = new LinkedList<>();
        prompt = "";
    }
    public static JsonObject errorJson(String info){
        JsonObject err = new JsonObject();
        err.addProperty("error", info);
        return err;
    }

    private static JsonArray buildTools(){
        JsonArray tools = new JsonArray();
        for(Tool t : Tools.TOOLS)
            if(t instanceof FuncTool tool)
                tools.add(tool.getSchema());
        return tools;
    }

    public static void sendReply(String content,ServerPlayer sender){
        if(content == null || content.isBlank()) return;
        MinecraftServer srv = server;
        if(srv == null) return;
        if(sender != null && content.contains(sender.getName().getString()))
            map.put(sender.getUUID(),true);
        srv.execute(() -> {
            ResolvableProfile profile = Util.buildProfile(UUID.fromString("2b856f35-91bb-4a09-80b6-6c81d7d28787"));
            MutableComponent mu = Component.literal("<").append(Component.object(new PlayerSprite(profile,true))).append(" ")
                    .append(PlayerUtils.getPlayerTitles("kei"))
                    .append("kei>")
                    .append(" ");
            for(ServerPlayer player : srv.getPlayerList().getPlayers())
                player.sendSystemMessage(mu.copy().append(content));
        });
    }

    public static void appendMsg(String msg,UUID user){
        keiConfig = Keri.getConfigManager().getConfig("drng:kei");
        ServerPlayer player = server.getPlayerList().getPlayer(user);
        String msgh = buildChatMsg(msg,player);

        if(!keiConfig.getKey("use_puca_bot").getAsBoolean()){
            if(msgQueue.size() >= keiConfig.getKey("max_history_size").getAsInt()) {
                msgQueue.remove();
                if(prompt.indexOf('\n') != -1)
                    prompt = prompt.substring(prompt.indexOf('\n'));
            }
            if(!prompt.isBlank()) prompt += '\n';
            prompt += msgh;
        }
        msgQueue.offer(msgh);


    }
    private static void LogErr(String info){
        MinecraftServer srv = server;
        if(srv == null){
            LOGGER.error("kei错误: {}", info);
            return;
        }
        srv.execute(() -> {
            Component component = Component.literal("kei出现错误：").append(info).withStyle(ChatFormatting.RED);
            for(ServerPlayer p : srv.getPlayerList().getPlayers())
                p.sendSystemMessage(component);
        });
    }
    public static JsonObject getPlayerListAsJson(){
        JsonObject result = new JsonObject();
        result.add("players",new JsonArray());
        JsonArray playerList = result.get("players").getAsJsonArray();
        for(ServerPlayer player : server.getPlayerList().getPlayers()){
            if(!PlayerUtils.isFakePlayer(player)) {
                JsonObject key = new JsonObject();
                key.addProperty("uuid", player.getUUID().toString());
                key.addProperty("name", player.getName().getString());
                key.addProperty("x",player.getBlockX());
                key.addProperty("y",player.getBlockY());
                key.addProperty("z",player.getBlockZ());
                playerList.add(key);
            }
        }
        return result;
    }

    public static JsonObject getBlockListAsJson(int radius,int x,int y,int z,String dimension){
        MinecraftServer srv = server;
        if(srv == null) return errorJson("server 不可用");
        ServerLevel level;
        switch (dimension){
            case "the_nether" -> level = srv.getLevel(Level.NETHER);
            case "the_end" -> level = srv.getLevel(Level.END);
            default -> level = srv.getLevel(Level.OVERWORLD);
        }
        if(level == null) return errorJson("维度不存在");
        if(radius > 32) radius = 32;
        JsonObject result = new JsonObject();
        result.addProperty("dimension",dimension);
        result.add("center",buildPosAsJson(x,y,z));
        result.add("blocks",new JsonArray());
        int minX = x-(radius/2),maxX = x+(radius - (radius/2));
        int minY = y-1,maxY = y+radius-1;
        int minZ = z-(radius/2),maxZ = z+(radius - (radius/2));
        for(int i = minY;i<=maxY;i++){
            for(int j = minX;j<=maxX;j++){
                for(int k = minZ;k<=maxZ;k++){
                    BlockState blockState = level.getBlockState(new BlockPos(j,i,k));
                    if(blockState.isAir()) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                    JsonObject block = new JsonObject();
                    block.addProperty("type",id);
                    block.add("pos",buildPosAsJson(j,i,k));
                    result.get("blocks").getAsJsonArray().add(block);
                }
            }
        }
        return result;
    }


    public static JsonObject getEntitiesAtAsJson(int x,int y,int z,int radius,String dimension){
        MinecraftServer srv = server;
        if(srv == null) return errorJson("server 不可用");
        ServerLevel level;
        switch (dimension){
            case "the_nether" -> level = srv.getLevel(Level.NETHER);
            case "the_end" -> level = srv.getLevel(Level.END);
            default -> level = srv.getLevel(Level.OVERWORLD);
        }
        if(level == null) return errorJson("维度不存在");
        if(radius > 32) radius = 32;
        AABB box = new AABB(x - radius, y - 1, z - radius, x + radius, y + radius - 1, z + radius);
        JsonObject result = new JsonObject();
        result.addProperty("dimension",dimension);
        result.add("center",buildPosAsJson(x,y,z));
        result.add("entities",new JsonArray());
        for(Entity entity : level.getEntities(null,box)){
            JsonObject o = new JsonObject();
            o.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            o.addProperty("name",entity.getName().getString());
            o.addProperty("player",entity instanceof ServerPlayer);
            o.addProperty("uuid",entity.getUUID().toString());
            o.add("pos",buildPosAsJson(entity.getBlockX(),entity.getBlockY(),entity.getBlockZ()));
            result.get("entities").getAsJsonArray().add(o);
        }
        return result;
    }

    public static JsonObject getBiome(int x,int y,int z,String dimension){
        ServerLevel level;
        JsonObject result = new JsonObject();
        BlockPos pos = new BlockPos(x,y,z);
        switch (dimension){
            case "the_nether" -> level = server.getLevel(Level.NETHER);
            case "the_end" -> level = server.getLevel(Level.END);
            default -> level = server.getLevel(Level.OVERWORLD);
        }
        Holder<Biome> biomeHolder = level.getBiome(pos);
        String id = biomeHolder.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("unknown");

        result.add("pos",buildPosAsJson(x,y,z));
        result.addProperty("biome",id);
        return result;
    }

    public static JsonObject playerPickAsJson(ServerPlayer player){
        HitResult hit = player.pick(5.0D,0,true);
        JsonObject result = new JsonObject();
        if(hit.getType().equals(HitResult.Type.MISS)){
            return errorJson("没有指向方块或实体");
        }else if(hit instanceof BlockHitResult blockHitResult){
            BlockPos pos = blockHitResult.getBlockPos();
            BlockEntity entity = player.level().getBlockEntity(pos);
            result.addProperty("type","block");
            if(entity instanceof SignBlockEntity signBlockEntity){
                String frontText = "";
                String backText = "";
                for(Component component : signBlockEntity.getFrontText().getMessages(false))
                    frontText = frontText + component.getString() + "\n";
                for(Component component : signBlockEntity.getBackText().getMessages(false))
                    backText = backText + component.getString() + "\n";

                result.addProperty("id","minecraft:sign");
                result.addProperty("front_text",frontText);
                result.addProperty("back_text",backText);
            } else{
                result.addProperty("id",BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(blockHitResult.getBlockPos()).getBlock()).toString());
            }
        }else if(hit instanceof EntityHitResult entityHitResult){
            result.addProperty("type","entity");
            result.addProperty("id",entityHitResult.getType().name());
        }
        return result;
    }

    public static JsonObject getPlayerStatusAsJson(ServerPlayer player){
        JsonObject result = new JsonObject();
        result.addProperty("name",player.getName().getString());
        result.addProperty("uuid",player.getUUID().toString());

        result.addProperty("game_mode",player.gameMode.getGameModeForPlayer().getName());
        result.addProperty("is_operator",player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        result.add("pos",buildPosAsJson(player.getX(), player.getY(),player.getZ()));
        result.add("effects",new JsonArray());
        for(MobEffectInstance effectInstance : player.getActiveEffects()){
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect().value());
            result.get("effects").getAsJsonArray().add(id.toString());
        }
        return result;
    }

    public static JsonObject getInventoryAsJson(ServerPlayer player){
        Inventory inventory = player.getInventory();
        JsonArray mainInventory = new JsonArray();
        JsonArray hotBar = new JsonArray();
        JsonArray armors = new JsonArray();
        for(int i=0;i<=8;i++){
            ItemStack item = inventory.getItem(i);
            if(item.isEmpty()) continue;
            hotBar.add(getItemStackAsJson(item));
        }
        for(int i=9;i<=35;i++){
            ItemStack item = inventory.getItem(i);
            if(item.isEmpty()) continue;
            mainInventory.add(getItemStackAsJson(item));
        }
        for(int i=36;i<=39;i++){
            ItemStack item = inventory.getItem(i);
            if(item.isEmpty()) continue;
            armors.add(getItemStackAsJson(item));
        }
        ItemStack offHand = inventory.getItem(40);
        ItemStack mainHand = player.getMainHandItem();
        JsonObject result = new JsonObject();
        result.add("hotbar",hotBar);
        result.add("inventory",mainInventory);
        result.add("armors",armors);
        result.add("off_hand",offHand.isEmpty() ? JsonNull.INSTANCE : getItemStackAsJson(offHand));
        result.add("main_hand",mainHand.isEmpty() ? JsonNull.INSTANCE : getItemStackAsJson(mainHand));
        return result;
    }
    private static JsonObject getItemStackAsJson(ItemStack itemStack){
        JsonObject result = new JsonObject();
        result.addProperty("id",BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString());
        result.addProperty("name",itemStack.getHoverName().getString());
        JsonArray enchantments = new JsonArray();
        for(Map.Entry entry : itemStack.getEnchantments().entrySet()){
            Holder<Enchantment> holder = (Holder<Enchantment>)entry.getKey();
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("Unknown");
            JsonObject enchan = new JsonObject();
            enchan.addProperty("id",id);
            enchan.addProperty("level",(int)entry.getValue());
            enchantments.add(enchan);
        }
        result.add("enchantments",enchantments);
        return result;
    }

    private static JsonObject buildPosAsJson(Number x,Number y,Number z){
        JsonObject result = new JsonObject();
        result.addProperty("x",x);
        result.addProperty("y",y);
        result.addProperty("z",z);
        return result;
    }
}
