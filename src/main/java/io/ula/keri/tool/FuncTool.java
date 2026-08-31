package io.ula.keri.tool;

import com.google.gson.JsonObject;

public class FuncTool extends Tool {
    @FunctionalInterface
    public interface Func{
        public JsonObject func(JsonObject args);
    }
    protected JsonObject schema = new JsonObject();
    protected FuncTool.Func func;
    public FuncTool(String name,String desc,Property property,FuncTool.Func func){
        schema.addProperty("type","function");
        this.func = func;
        JsonObject function = new JsonObject();
        function.addProperty("name",name);
        function.addProperty("description",desc);
        if(property.getType().equals("object"))
            function.add("parameters",property.build());
        else{
            JsonObject parameters = new JsonObject();
            parameters.addProperty("type","object");
            JsonObject properties = new JsonObject();
            properties.add(property.getId(),property.build());
            parameters.add("properties",properties);
            parameters.addProperty("additionalProperties",false);
            function.add("parameters",parameters);
        }
        schema.add("function",function);
    }

    public JsonObject getSchema(){
        return schema;
    }

    public String getName(){
        return schema.getAsJsonObject("function").get("name").getAsString();
    }

    public JsonObject func(JsonObject args){
        return func.func(args);
    }
}