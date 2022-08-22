package com.couchbeans;

import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.query.QueryOptions;

import java.util.Arrays;
import java.util.List;
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
}
