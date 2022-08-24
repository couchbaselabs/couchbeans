package com.couchbeans;

import com.couchbase.client.dcp.deps.io.netty.handler.codec.serialization.ObjectEncoder;
import com.couchbase.client.java.codec.DefaultJsonSerializer;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.query.QueryOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Converter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Utils {
    public static String collectionName(Class from) {
        return from.getCanonicalName()
                .replaceAll("\\.", "-");
    }

    public static Class collectionClass(String collectionName) throws ClassNotFoundException {
        return Class.forName(collectionName.replaceAll("-", "."));
    }

    public static String collectionRef(String collection) {
        return String.format("`%s`.`%s`.`%s`", Couchbeans.CBB_BUCKET, Couchbeans.CBB_SCOPE, collection);
    }

    public static String inlineCollectionRef(String collection) {
        return String.format("%s_%s_%s", Couchbeans.CBB_BUCKET, Couchbeans.CBB_SCOPE, collection);
    }

    public static void createCollectionIfNotExists(String collection) {
        Couchbeans.CLUSTER.query(String.format("CREATE COLLECTION %s IF NOT EXISTS", collectionRef(collection)));
    }

    public static void createCollectionIfNotExists(Class type) {
        createCollectionIfNotExists(collectionName(type));
    }

    public static void createPrimaryIndexIfNotExists(String collection) {
        Couchbeans.CLUSTER.query(String.format("CREATE PRIMARY INDEX `idx_%s_primary` IF NOT EXISTS ON %s",
                inlineCollectionRef(collection),
                collectionRef(collection)
        ));
    }

    public static String nameList(String... fields) {
        return Arrays.stream(fields)
                .collect(Collectors.joining(","));
    }

    public static void createIndexIfNotExists(String collection, String... fields) {
        Couchbeans.CLUSTER.query(String.format("CREATE INDEX `idx_%s` IF NOT EXISTS ON %s(%s) USING GSI",
                inlineCollectionRef(collection),
                collectionRef(collection),
                nameList(fields)
        ));
    }

    public static String envOrDefault(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }

    public static List<BeanLink> findLinkedBeans(Object source) {
        String sourceKey = Couchbeans.KEY.get(source);
        if (sourceKey == null) {
            throw new IllegalArgumentException("Unknown bean: " + source);
        }

        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(),
                        sourceKey
                ))
        ).rowsAs(BeanLink.class);
    }

    public static List<BeanLink> findLinkedBeans(Object source, Class target) {

        String sourceKey = Couchbeans.KEY.get(source);
        if (sourceKey == null) {
            throw new IllegalArgumentException("Unknown bean: " + source);
        }

        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2 AND targetType = $3",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(),
                        sourceKey,
                        target.getCanonicalName()
                ))
        ).rowsAs(BeanLink.class);
    }

    public static List<BeanMethod> findConsumers(String beanType, String name, JsonArray arguments, JsonArray except) {
        return Optional.ofNullable(
                        Couchbeans.SCOPE.query(
                                String.format("SELECT * FROM %s WHERE `beanType` LIKE $1 `name` LIKE $2 AND EVERY v IN `arguments` SATISFIES v IN $3 END AND META().id NOT IN $4", collectionRef(collectionName(BeanMethod.class))),
                                QueryOptions.queryOptions().parameters(JsonArray.from(
                                        beanType, name, arguments, except
                                ))
                        )
                ).map(gr -> gr.rowsAs(BeanMethod.class))
                .orElseGet(Collections::emptyList);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> Optional<T> fetchObject(Class<T> type, String key) {
        return Optional.ofNullable(Couchbeans.SCOPE.collection(collectionName(type)))
                .map(c -> c.get(key))
                .filter(Objects::nonNull)
                .map(gr -> gr.contentAs(type));
    }

    protected static Set<String> inheritanceDescriptorChain(Class of) {
        if (of != Object.class) {
            Set<String> parent = inheritanceDescriptorChain(of.getSuperclass());
            parent.add(of.descriptorString());
            return parent;
        }
        HashSet<String> root = new HashSet<>();
        root.add(Object.class.descriptorString());
        return root;
    }

    public static Set<String> inheritanceChain(Class of) {
        if (of != Object.class) {
            Set<String> parent = inheritanceChain(of.getSuperclass());
            parent.add(of.getCanonicalName());
            return parent;
        }

        HashSet<String> root = new HashSet<>();
        root.add(Object.class.getCanonicalName());
        return root;
    }
}
