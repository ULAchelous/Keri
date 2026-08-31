package io.ula.keri.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NetUtil {
    public static JsonObject requestOpenAIAPIAsJson(URL baseUrl, JsonObject jsonBody, int ttl, int readTimeOut, String key){

        Gson gson = new GsonBuilder().create();
        String body = gson.toJson(jsonBody);
        if(body.isBlank()) return new JsonObject();
        try {
            HttpURLConnection connection = (HttpURLConnection) baseUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(ttl);
            connection.setReadTimeout(readTimeOut);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization","Bearer "+key);

            try(OutputStream os = connection.getOutputStream()){
                byte[] input = body.getBytes("UTF-8");
                os.write(input,0,input.length);
            }

            int code = connection.getResponseCode();
            if(code == 200){
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return JsonParser.parseString(response.toString()).getAsJsonObject();
                }
            }else{
                //非 200：把服务端返回的错误信息（如 401/403/429 的 json body）带出来，方便调用方展示
                InputStream errorStream = connection.getErrorStream();
                if(errorStream != null){
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        JsonObject err = new JsonObject();
                        err.addProperty("http_code", code);
                        try {
                            err.add("error", JsonParser.parseString(response.toString()));
                        }catch (Exception ignored){
                            err.addProperty("error", response.toString());
                        }
                        return err;
                    }
                }
                throw new RuntimeException("Http response code:" + code);
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
