package com.couchbeans;

import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.fasterxml.jackson.databind.ser.Serializers;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class BeanMethod {
    private final String className;
    private final String name;
    private final List<String> arguments;

    private final String hash;

    public BeanMethod(String className, String name, List<String> arguments) {
        this.className = className;
        this.name = name;
        this.arguments = arguments;

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(toString().getBytes());
            this.hash = new BigInteger(1, messageDigest.digest()).toString(34);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public BeanMethod(String className, String name) {
        this(className, name, Collections.EMPTY_LIST);
    }

    public BeanMethod(JsonObject source) {
        this(source.getString("className"), source.getString("name"), new LinkedList<>());
        source.getArray("arguments").forEach(s -> arguments.add((String) s));
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return new StringBuilder(this.className)
                .append("::")
                .append(name)
                .append(arguments.stream().collect(Collectors.joining(",", "(", ")")))
                .toString();
    }

    public String getHash() {
        return hash;
    }

    public JsonObject toJsonObject() {
        JsonObject result = JsonObject.create();
        result.put("className", className);
        result.put("name", name);
        JsonArray args = JsonArray.create();
        arguments.stream().forEach(args::add);
        result.put("arguments", arguments);
        return result;
    }
}