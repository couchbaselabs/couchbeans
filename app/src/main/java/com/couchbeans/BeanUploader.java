package com.couchbeans;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import javassist.bytecode.AnnotationDefaultAttribute;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.ByteMemberValue;
import javassist.bytecode.annotation.CharMemberValue;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.DoubleMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.FloatMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.LongMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.ShortMemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BeanUploader {
    private static Cluster cluster;
    private static Bucket bucket;
    private static Scope scope;
    private static Collection
            classCollection,
            metaCollection;

    private static Map<String, String> env;

    public static void main(String[] args) {
        cluster = Cluster.connect(
                System.getenv("CBB_ADDRESS"),
                System.getenv("CBB_USERNAME"),
                System.getenv("CBB_PASSWORD")
        );
        bucket = cluster.bucket(System.getenv("CBB_BUCKET"));
        scope = bucket.scope(System.getenv("CBB_SCOPE"));
        env = System.getenv();
        classCollection = scope.collection(env.getOrDefault("CBB_CLASS_STORE", ("cbb_classes")));
        metaCollection = scope.collection(env.getOrDefault("CBB_META_STORE", ("cbb_meta")));

        processPaths(args);
    }

    public static void main(String[] sources, Collection meta, Collection classes) {
        metaCollection = meta;
        classCollection = classes;
        processPaths(sources);
    }

    private static void processPaths(String[] sources) {
        if (sources.length == 0) {
            sources = new String[]{System.getProperty("user.dir")};
        }
        Arrays.stream(sources)
                .map(Path::of)
                .forEach(BeanUploader::processPath);
    }

    private static void processPath(Path path) {
        try {
            if (path.toFile().isDirectory()) {
                processDirectory(path);
            } else {
                processFile(path);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files
                    .filter(f -> f.endsWith(".class") || f.toFile().isDirectory())
                    .forEach(BeanUploader::processPath);
        }
    }

    private static void processFile(Path path) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(new FileInputStream(path.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(bin));
        String name = cf.getName();

        GetResult r = metaCollection.get(name);
        JsonObject meta = (r != null) ? r.contentAsObject() : JsonObject.create();

        meta.put("name", name);
        JsonArray interfaces = JsonArray.create();
        Arrays.stream(cf.getInterfaces()).forEach(interfaces::add);
        meta.put("interfaces", interfaces);

        JsonArray metaAnnotations = JsonArray.create();
        meta.put("annotations", metaAnnotations);
        JsonArray methods = JsonArray.create();
        meta.put("methods", methods);
        ((List<AttributeInfo>) cf.getAttributes()).stream()
                .forEach(ai -> processAtributeInfo(ai, metaAnnotations));

        ((List<MethodInfo>)cf.getMethods()).stream()
                .forEach(mi -> {
                    JsonObject methodInfo = JsonObject.create();
                    methods.add(methodInfo);
                    methodInfo.put("name", mi.getName());
                    methodInfo.put("descriptor", mi.getDescriptor());
                    JsonArray methodAnnotations = JsonArray.create();
                    methodInfo.put("annotations", methodAnnotations);
                    ((List<AttributeInfo>)mi.getAttributes()).stream()
                            .forEach(ai -> processAtributeInfo(ai, methodAnnotations));
                });

        metaCollection.upsert(name, meta);
    }

    private static void processAtributeInfo(AttributeInfo ai, JsonArray target) {
        if (ai instanceof AnnotationsAttribute) {
            Arrays.stream(((AnnotationsAttribute) ai).getAnnotations())
                    .map(BeanUploader::processAnnotation)
                    .forEach(target::add);
        } else if (ai instanceof AnnotationDefaultAttribute) {
            JsonObject ann = JsonObject.create();
            ann.put("name", ai.getName());
            target.add(ann);
        }
    }

    private static JsonObject processAnnotation(Annotation annotation) {
        JsonObject result = JsonObject.create();
        result.put("__class", annotation.getTypeName());
        Set<String> memberNames = annotation.getMemberNames();
        if (memberNames != null) {
            memberNames.stream()
                    .forEach(name -> {
                        Object value = processValue(annotation.getMemberValue(name));
                        result.put(name, value);
                    });
        }
        return result;
    }

    private static Object processValue(MemberValue value) {
        if (value instanceof AnnotationMemberValue) {
            return processAnnotation(((AnnotationMemberValue) value).getValue());
        } else if (value instanceof ArrayMemberValue) {
            return JsonArray.from(
                    Arrays.stream(((ArrayMemberValue) value).getValue())
                            .map(BeanUploader::processValue)
                            .collect(Collectors.toList()));
        } else if (value instanceof BooleanMemberValue) {
            return ((BooleanMemberValue) value).getValue();
        } else if (value instanceof ByteMemberValue) {
            return ((ByteMemberValue) value).getValue();
        } else if (value instanceof CharMemberValue) {
            return ((CharMemberValue) value).getValue();
        } else if (value instanceof ClassMemberValue) {
            return ((ClassMemberValue) value).getValue();
        } else if (value instanceof DoubleMemberValue) {
            return ((DoubleMemberValue) value).getValue();
        } else if (value instanceof EnumMemberValue) {
            return ((EnumMemberValue) value).getValue();
        } else if (value instanceof FloatMemberValue) {
            return ((FloatMemberValue) value).getValue();
        } else if (value instanceof IntegerMemberValue) {
            return ((IntegerMemberValue) value).getValue();
        } else if (value instanceof LongMemberValue) {
            return ((LongMemberValue) value).getValue();
        } else if (value instanceof ShortMemberValue) {
            return ((ShortMemberValue) value).getValue();
        } else if (value instanceof StringMemberValue) {
            return ((StringMemberValue) value).getValue();
        } else {
            throw new IllegalArgumentException("Unknown annotation value type: " + value.getClass().getCanonicalName());
        }
    }

    private static void validateArguments(String[] args) {

    }
}