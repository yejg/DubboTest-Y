package com.yanglx.dubbo.test.utils;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;

public class JsonUtils {

    private static final ExclusionStrategy exclusionStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            // 根据字段名过滤
            return "serialVersionUID".equals(fieldAttributes.getName());
        }

        @Override
        public boolean shouldSkipClass(Class<?> aClass) {
            return false;
        }
    };

    private static final Gson prettyGson = (new GsonBuilder())
            .setPrettyPrinting()
            .serializeNulls().setExclusionStrategies(exclusionStrategy)
            .create();

    private static final Gson uglyGson = (new GsonBuilder())
            .serializeNulls().setExclusionStrategies(exclusionStrategy)
            .create();

    public static String toJSONString(Object obj) {
        return uglyGson.toJson(obj);
    }

    public static String toPrettyJSONString(Object obj) {
        return prettyGson.toJson(obj);
    }

    public static <T> T toJava(String json, Class<T> tClass) {
        return uglyGson.fromJson(json,tClass);
    }

    public static <T> List<T> toJavaList(String json, Class<T> tClass) {
        List<T> list = new ArrayList<T>();
        JsonArray jsonElements = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement elem : jsonElements) {
            list.add(uglyGson.fromJson(elem, tClass));
        }
        return list;
    }
}
