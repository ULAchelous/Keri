package io.ula.keri.tool;

import io.ula.drng.utils.kei.KeiChatBotUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class Tools {
    public static final List<Tool> TOOLS = List.of(
            new FuncTool("get_player_list",
                    "获取当前服务器在线玩家列表（不含假人）。当玩家询问\"谁在线\"\"现在有哪些人在线\"\"服务器里有人吗\"或者你遇到不认识的uuid发送消息等需要实时在线信息的问题时调用。返回 {\"players\":[{\"uuid\":\"...\",\"name\":\"...\",\"x\":..,\"y\":..,\"z\":..}]}。",
                    Property.object("", ""),
                    args -> KeiChatBotUtils.getPlayerListAsJson()),
            new FuncTool("query_blocks",
                    "获取指定坐标周围的方块列表（以 (x,y,z) 为中心，横向纵向各扫描 radius/2 的立方体区域，radius 上限 32，空气方块不列出）。当玩家询问\"某个坐标有什么方块\"\"xx 位置附近是什么\"\"查一下这个地方\"时调用。返回 {\"dimension\":\"overworld\",\"center\":{\"x\":..,\"y\":..,\"z\":..},\"blocks\":[{\"type\":\"minecraft:stone\",\"pos\":{\"x\":..,\"y\":..,\"z\":..}}]}。",
                    Property.object("", "查询参数")
                            .field(
                                    Property.integer("x", "中心 X 坐标"),
                                    Property.integer("y", "中心 Y 坐标"),
                                    Property.integer("z", "中心 Z 坐标"),
                                    Property.integer("radius", "扫描半径，1-32"),
                                    Property.string("dimension", "维度，可选：overworld/the_nether/the_end，默认 overworld")
                            )
                            .required("x", "y", "z", "radius"),
                    args -> {
                        if(!args.has("x") || !args.has("y") || !args.has("z"))
                            return KeiChatBotUtils.errorJson("缺少坐标参数 x/y/z");
                        int x = args.get("x").getAsInt();
                        int y = args.get("y").getAsInt();
                        int z = args.get("z").getAsInt();
                        int radius = args.has("radius") ? args.get("radius").getAsInt() : 4;
                        String dimension = args.has("dimension") ? args.get("dimension").getAsString() : "overworld";
                        return KeiChatBotUtils.getBlockListAsJson(radius,x, y, z, dimension);
                    }),
            new FuncTool("get_biome",
                    "获取指定坐标的生物群系。当玩家询问\"这是什么生物群系\"\"这里是什么地形\"\"这个位置在哪里\"时调用。返回 {\"pos\":{\"x\":..,\"y\":..,\"z\":..},\"biome\":\"minecraft:plains\"}。",
                    Property.object("", "查询参数")
                            .field(
                                    Property.integer("x", "X 坐标"),
                                    Property.integer("y", "Y 坐标"),
                                    Property.integer("z", "Z 坐标"),
                                    Property.string("dimension", "维度，可选：overworld/the_nether/the_end，默认 overworld")
                            )
                            .required("x", "y", "z"),
                    args -> {
                        if(!args.has("x") || !args.has("y") || !args.has("z"))
                            return KeiChatBotUtils.errorJson("缺少坐标参数 x/y/z");
                        int x = args.get("x").getAsInt();
                        int y = args.get("y").getAsInt();
                        int z = args.get("z").getAsInt();
                        String dimension = args.has("dimension") ? args.get("dimension").getAsString() : "overworld";
                        return KeiChatBotUtils.getBiome(x, y, z, dimension);
                    }),
            new FuncTool("scan_entities",
                    "扫描指定坐标周围的实体（怪物、动物、掉落物、玩家等，半径 1-32）。当玩家询问\"附近有什么怪物\"\"周围有敌人吗\"\"这个地方有什么生物\"时调用。返回 {\"dimension\":\"overworld\",\"center\":{\"x\":..,\"y\":..,\"z\":..},\"entities\":[{\"type\":\"minecraft:zombie\",\"name\":\"僵尸\",\"player\":false,\"uuid\":\"...\",\"pos\":{\"x\":..,\"y\":..,\"z\":..}}]}。",
                    Property.object("", "查询参数")
                            .field(
                                    Property.integer("x", "中心 X 坐标"),
                                    Property.integer("y", "中心 Y 坐标"),
                                    Property.integer("z", "中心 Z 坐标"),
                                    Property.integer("radius", "扫描半径，1-32"),
                                    Property.string("dimension", "维度，可选：overworld/the_nether/the_end，默认 overworld")
                            )
                            .required("x", "y", "z", "radius"),
                    args -> {
                        if(!args.has("x") || !args.has("y") || !args.has("z"))
                            return KeiChatBotUtils.errorJson("缺少坐标参数 x/y/z");
                        int x = args.get("x").getAsInt();
                        int y = args.get("y").getAsInt();
                        int z = args.get("z").getAsInt();
                        int radius = args.has("radius") ? args.get("radius").getAsInt() : 8;
                        String dimension = args.has("dimension") ? args.get("dimension").getAsString() : "overworld";
                        return KeiChatBotUtils.getEntitiesAtAsJson(x, y, z, radius, dimension);
                    }),
            new FuncTool("look_at",
                    "获取指定玩家视线指向的方块或实体（距离 5 格内，包含流体）。uuid 取当前对话玩家的 UUID。当玩家询问\"我看的是什么\"\"我面前的方块是什么\"\"看着告示牌读内容\"\"我瞄准了什么\"时调用。指向方块返回 {\"type\":\"block\",\"id\":\"minecraft:stone\"}（告示牌额外含 front_text/back_text 两行文本），指向实体返回 {\"type\":\"entity\",\"id\":\"minecraft:zombie\"}。",
                    Property.object("", "查询参数")
                            .field(Property.string("uuid", "目标玩家的 UUID"))
                            .required("uuid"),
                    args -> {
                        if(!args.has("uuid"))
                            return KeiChatBotUtils.errorJson("缺少参数 uuid");
                        ServerPlayer player = KeiChatBotUtils.getPlayerByUuid(args.get("uuid").getAsString());
                        if(player == null)
                            return KeiChatBotUtils.errorJson("目标玩家不在线或 UUID 无效");
                        return KeiChatBotUtils.playerPickAsJson(player);
                    }),
            new FuncTool("get_player_status",
                    "获取指定玩家的基础状态：名字、UUID、坐标、状态效果列表、游戏模式、是否 OP。背包物品请使用 get_inventory 工具。uuid 取当前对话玩家的 UUID。当玩家询问\"我的状态\"\"我有什么 buff\"时调用。返回 {\"name\":\"...\",\"uuid\":\"...\",\"pos\":{\"x\":..,\"y\":..,\"z\":..},\"game_mode\":\"survival\",\"is_operator\":false,\"effects\":[\"minecraft:speed\",...]}。",
                    Property.object("", "查询参数")
                            .field(Property.string("uuid", "目标玩家的 UUID"))
                            .required("uuid"),
                    args -> {
                        if(!args.has("uuid"))
                            return KeiChatBotUtils.errorJson("缺少参数 uuid");
                        ServerPlayer player = KeiChatBotUtils.getPlayerByUuid(args.get("uuid").getAsString());
                        if(player == null)
                            return KeiChatBotUtils.errorJson("目标玩家不在线或 UUID 无效");
                        return KeiChatBotUtils.getPlayerStatusAsJson(player);
                    }),
            new FuncTool("get_inventory",
                    "获取指定玩家的背包物品：快捷栏、主物品栏、盔甲、副手、主手（含显示名与附魔，空槽不列出）。uuid 取当前对话玩家的 UUID。当玩家询问\"我的装备\"\"背包里有什么\"\"我拿了什么\"时调用。返回 {\"hotbar\":[{...}],\"inventory\":[{...}],\"armors\":[{...}],\"off_hand\":{...},\"main_hand\":{...}}，物品格式 {\"id\":\"minecraft:stone\",\"name\":\"石头\",\"enchantments\":[{\"id\":\"minecraft:sharpness\",\"level\":5}]}。",
                    Property.object("", "查询参数")
                            .field(Property.string("uuid", "目标玩家的 UUID"))
                            .required("uuid"),
                    args -> {
                        if(!args.has("uuid"))
                            return KeiChatBotUtils.errorJson("缺少参数 uuid");
                        ServerPlayer player = KeiChatBotUtils.getPlayerByUuid(args.get("uuid").getAsString());
                        if(player == null)
                            return KeiChatBotUtils.errorJson("目标玩家不在线或 UUID 无效");
                        return KeiChatBotUtils.getInventoryAsJson(player);
                    })
    );
}
