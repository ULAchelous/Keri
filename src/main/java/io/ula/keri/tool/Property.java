package io.ula.keri.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Property {
    protected JsonObject content = new JsonObject();
    protected String id;
    protected String type;
    private Property(String type,String id,String desc){
        this.id = id;
        this.type = type;
        content.addProperty("type",type);
        if(desc != null && !desc.isBlank())
            content.addProperty("description",desc);
    }


    public String getId(){
        return id;
    }
    public String getType(){
        return type;
    }
    public Property setEnum(String... key){
        if(!this.type.equals("string"))
            return this;
        if(content.has("enum"))
            content.remove("enum");
        content.add("enum",new JsonArray());
        for(String str : key)
            content.get("enum").getAsJsonArray().add(str);
        return this;
    }

    public Property minLen(long len){
        if(!this.type.equals("string"))
            return this;
        if(content.has("minLength"))
            content.remove("minLength");
        content.addProperty("minLength",len);
        return this;
    }
    public Property maxLen(long len){
        if(!this.type.equals("string"))
            return this;
        if(content.has("maxLength"))
            content.remove("maxLength");
        content.addProperty("maxLength",len);
        return this;
    }

    public Property mininum(long num){
        if(!(this.type.equals("integer") || this.type.equals("number")))
            return this;
        setNum(false,num);
        return this;
    }
    public Property maxinum(long num){
        if(!(this.type.equals("integer") || this.type.equals("number")))
            return this;
        setNum(true,num);
        return this;
    }
    public Property mininum(double num){
        if(!(this.type.equals("integer") || this.type.equals("number")))
            return this;
        setNum(false,num);
        return this;
    }
    public Property maxinum(double num){
        if(!(this.type.equals("integer") || this.type.equals("number")))
            return this;
        setNum(true,num);
        return this;
    }
    private void setNum(Boolean b,Number num){
        String s = "minimum";
        if(b) s = "maximum";
        if(content.has(s))
            content.remove(s);
        content.addProperty(s,num);
    }


    public Property field(Property... children){
        if(!this.type.equals("object"))
            return this;
        for(Property child : children)
            getProperties().add(child.getId(), child.build());
        return this;
    }
    public Property required(String... names){
        if(!this.type.equals("object"))
            return this;
        JsonArray arr;
        if(content.has("required"))
            arr = content.get("required").getAsJsonArray();
        else {
            arr = new JsonArray();
            content.add("required", arr);
        }
        for(String n : names)
            arr.add(n);
        return this;
    }
    public Property additionalProperties(boolean allow){
        if(!this.type.equals("object"))
            return this;
        if(content.has("additionalProperties"))
            content.remove("additionalProperties");
        content.addProperty("additionalProperties", allow);
        return this;
    }
    private JsonObject getProperties(){
        if(!content.has("properties"))
            content.add("properties", new JsonObject());
        return content.get("properties").getAsJsonObject();
    }


    public Property items(Property item){
        if(!this.type.equals("array"))
            return this;
        if(content.has("items"))
            content.remove("items");
        content.add("items", item.build());
        return this;
    }

    public JsonObject build(){
        return content;
    }

    public static Property string(String id, String desc){
        return new Property("string",id,desc);
    }
    public static Property integer(String id, String desc){
        return new Property("integer",id,desc);
    }
    public static Property number(String id, String desc){
        return new Property("number",id,desc);
    }
    public static Property bool(String id, String desc){
        return new Property("boolean",id,desc);
    }
    public static Property array(String id, String desc){
        return new Property("array",id,desc);
    }
    public static Property object(String id,String desc){
        return new Property("object",id,desc);
    }
}
