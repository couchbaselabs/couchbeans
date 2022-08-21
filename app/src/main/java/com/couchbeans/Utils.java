package com.couchbeans;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Utils {
    public static String collectionName(Class from) {
        return from.getCanonicalName()
                .replaceAll("\\.", "-");
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
}
