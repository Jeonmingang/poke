package com.minkang.ultimate.pocketphoto.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonUtil {
    public static String encode(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('\"').append(escape(e.getKey())).append('\"').append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(String.valueOf(v));
            } else if (v instanceof Iterable) {
                sb.append('[');
                boolean f2 = true;
                for (Object o : (Iterable<?>) v) {
                    if (!f2) sb.append(',');
                    f2 = false;
                    sb.append('\"').append(escape(String.valueOf(o))).append('\"');
                }
                sb.append(']');
            } else if (v == null) {
                sb.append("null");
            } else {
                sb.append('\"').append(escape(String.valueOf(v))).append('\"');
            }
        }
        sb.append('}');
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static Map<String, Object> decode(String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            return parseSimple(json);
        } catch (Throwable t) {
            return new LinkedHashMap<>();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, Object> parseSimple(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        String body = json.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1).trim();
        }
        if (body.isEmpty()) return map;
        String[] parts = body.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim();
            String v = kv[1].trim();
            if (k.startsWith("\"") && k.endsWith("\"")) k = k.substring(1, k.length() - 1);
            if (v.equals("true") || v.equals("false")) {
                map.put(k, Boolean.parseBoolean(v));
            } else if (v.matches("-?\\d+")) {
                map.put(k, Integer.parseInt(v));
            } else if (v.startsWith("[")) {
                String arr = v.substring(1, v.length() - 1).trim();
                if (arr.isEmpty()) { map.put(k, new java.util.ArrayList<>()); continue; }
                String[] elems = arr.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
                java.util.List<String> list = new java.util.ArrayList<>();
                for (String el : elems) {
                    el = el.trim();
                    if (el.startsWith("\"") && el.endsWith("\"")) el = el.substring(1, el.length() - 1);
                    list.add(el.replace("\\\"", "\""));
                }
                map.put(k, list);
            } else if (v.equals("null")) {
                map.put(k, null);
            } else {
                if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
                map.put(k, v.replace("\\\"", "\""));
            }
        }
        return map;
    }
}
