package com.couchbeans;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import javassist.bytecode.AnnotationDefaultAttribute;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.MethodParametersAttribute;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class BeanUploader {
    private static Collection
            classCollection,
            metaCollection;

    private static Map<String, String> env;

    public static void main(String[] args) {
        classCollection = Couchbeans.SCOPE.collection(App.CLASS_COLLECTION_NAME);
        metaCollection = Couchbeans.SCOPE.collection(App.METHOD_COLLECTION_NAME);

        ensureDbStructure();
        processPaths(args);
    }

    public static void main(String[] sources, Collection meta, Collection classes) {
        metaCollection = meta;
        classCollection = classes;
        processPaths(sources);
    }

    protected static void ensureDbStructure() {
        Utils.createCollectionIfNotExists(App.CLASS_COLLECTION_NAME);
        Utils.createPrimaryIndexIfNotExists(App.CLASS_COLLECTION_NAME);
        Utils.createCollectionIfNotExists(App.METHOD_COLLECTION_NAME);
        Utils.createPrimaryIndexIfNotExists(App.METHOD_COLLECTION_NAME);
        Utils.createIndexIfNotExists(App.METHOD_COLLECTION_NAME, "className", "name", "arguments");
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
            System.out.println("Processing path: " + path.toString() + " (is jar: " + path.toString().endsWith(".jar") + ")");
            if (path.toFile().isDirectory()) {
                processDirectory(path);
            } else if (path.toString().endsWith(".jar")) {
                processJar(path);
            } else {
                processFile(path);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processJar(Path path) throws Exception {
        processZipStream(new FileInputStream(path.toFile()));
    }

    private static void processZipStream(InputStream is) throws Exception {
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                if (entry.getName().endsWith(".class")) {
                    processClass(zis);
                } else if (entry.getName().endsWith(".jar")) {
                    processZipStream(zis);
                }
            }
            zis.closeEntry();
        }
    }

    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files
                    .filter(f -> f.endsWith(".class") || f.toFile().isDirectory())
                    .forEach(BeanUploader::processPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process directory '" + directory.toString() + "'" , e);
        }
    }

    private static void processFile(Path path) throws Exception {
        try {
            processClass(new FileInputStream(path.toFile()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file '" + path.toString() + "'", e);
        }
    }

    private static void processClass(InputStream source) throws Exception {
        BufferedInputStream bin = new BufferedInputStream(source);
        ClassFile cf = new ClassFile(new DataInputStream(bin));
        String name = cf.getName();

        ((List<MethodInfo>) cf.getMethods()).stream()
                .forEach(mi -> {
                    BeanMethod method = new BeanMethod(cf.getName(), mi.getName(), getMethodArguments(mi));
                    metaCollection.upsert(method.getHash(), method.toJsonObject());
                });
    }

    private static List<String> getMethodArguments(MethodInfo info) {
        ConstPool cpool = info.getConstPool();
        LocalVariableAttribute table = (LocalVariableAttribute) info.getCodeAttribute().getAttribute(LocalVariableAttribute.tag);
        MethodParametersAttribute params = (MethodParametersAttribute) info.getAttribute(MethodParametersAttribute.tag);

        if (table != null && params != null) {
            int[] paramNames = IntStream.range(0, params.size()).map(params::name).toArray();
            return IntStream.range(0, table.tableLength())
                    .filter(i -> Arrays.binarySearch(paramNames, table.nameIndex(i)) > -1)
                    .boxed()
                    .map(table::descriptor)
                    .collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
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