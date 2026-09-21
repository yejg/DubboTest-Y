package com.yanglx.dubbo.test.utils;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
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

    /**
     * 数字读成 Object 时的类型策略.
     *
     * <p>Gson 默认把所有数字都读成 Double, 于是 13800138000 这种 id/时间戳在界面上变成
     * 1.38001380E10, 泛化调用时传给 provider 的也是 Double。这里按字面量还原:
     * 整数 -> Long(超出范围用 BigInteger), 小数 -> Double。
     */
    private static final ToNumberStrategy NUMBER_STRATEGY = in -> {
        String value = in.nextString();
        if (value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException e) {
                return new BigInteger(value);
            }
        }
        return Double.valueOf(value);
    };

    /** 浮点数写出时用普通计数法, 不用科学计数法 */
    private static final TypeAdapter<Double> DOUBLE_ADAPTER = new TypeAdapter<Double>() {
        @Override
        public void write(JsonWriter out, Double value) throws IOException {
            if (value == null || value.isNaN() || value.isInfinite()) {
                // 交给 Gson 按原有规则处理(非 lenient 模式下 NaN/Infinity 会报错)
                out.value(value);
                return;
            }
            out.value(plain(BigDecimal.valueOf(value), value));
        }

        @Override
        public Double read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return in.nextDouble();
        }
    };

    private static final TypeAdapter<Float> FLOAT_ADAPTER = new TypeAdapter<Float>() {
        @Override
        public void write(JsonWriter out, Float value) throws IOException {
            if (value == null || value.isNaN() || value.isInfinite()) {
                out.value(value);
                return;
            }
            out.value(plain(new BigDecimal(Float.toString(value)), value));
        }

        @Override
        public Float read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return (float) in.nextDouble();
        }
    };

    private static final TypeAdapter<BigDecimal> BIG_DECIMAL_ADAPTER = new TypeAdapter<BigDecimal>() {
        @Override
        public void write(JsonWriter out, BigDecimal value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(plain(value, value));
        }

        @Override
        public BigDecimal read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return new BigDecimal(in.nextString());
        }
    };

    private static final Gson prettyGson = newBuilder().setPrettyPrinting().create();

    private static final Gson uglyGson = newBuilder().create();

    private static GsonBuilder newBuilder() {
        return new GsonBuilder()
                .serializeNulls()
                .setExclusionStrategies(exclusionStrategy)
                .setObjectToNumberStrategy(NUMBER_STRATEGY)
                .registerTypeAdapter(Double.class, DOUBLE_ADAPTER)
                .registerTypeAdapter(double.class, DOUBLE_ADAPTER)
                .registerTypeAdapter(Float.class, FLOAT_ADAPTER)
                .registerTypeAdapter(float.class, FLOAT_ADAPTER)
                .registerTypeAdapter(BigDecimal.class, BIG_DECIMAL_ADAPTER);
    }

    /**
     * JsonWriter 写数字时用的是 Number#toString(), 这里包一层, 让它拿到普通计数法的字符串.
     *
     * @param decimal 用于生成普通计数法字符串
     * @param origin  原始数字, 各 xxxValue() 仍按原值返回
     */
    private static Number plain(BigDecimal decimal, Number origin) {
        String plain = decimal.toPlainString();
        return new Number() {
            @Override
            public int intValue() {
                return origin.intValue();
            }

            @Override
            public long longValue() {
                return origin.longValue();
            }

            @Override
            public float floatValue() {
                return origin.floatValue();
            }

            @Override
            public double doubleValue() {
                return origin.doubleValue();
            }

            @Override
            public String toString() {
                return plain;
            }
        };
    }

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
        JsonArray jsonElements = uglyGson.fromJson(json, JsonArray.class);
        for (JsonElement elem : jsonElements) {
            list.add(uglyGson.fromJson(elem, tClass));
        }
        return list;
    }
}
