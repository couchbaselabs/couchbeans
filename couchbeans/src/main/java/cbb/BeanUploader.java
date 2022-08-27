package cbb;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.codec.RawBinaryTranscoder;
import com.couchbase.client.java.kv.UpsertOptions;
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BeanUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeanUploader.class);
    private static Collection
            classCollection,
            metaCollection;

    private static Map<String, String> env;
    private static int binaryDocument;

    public static void main(String[] args) {
        run(args);
    }

    public static void run(String[] args) {
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
        Utils.createCollectionIfNotExists(ClassInfo.COLLECTION);
        Utils.createIndexIfNotExists(ClassInfo.COLLECTION, "className", "beanType");
    }

    private static void processPaths(String[] sources) {
        if (sources.length == 0) {
            sources = new String[]{System.getProperty("user.dir")};
        }
        ClassPool cp = new ClassPool();
        cp.insertClassPath(new ClassClassPath(BeanUploader.class));
        Arrays.stream(sources)
                .map(Path::of)
                .flatMap(path -> processPath(cp, path).stream())
                .forEach(ci -> System.out.println(String.format("Successfully uploaded type '%s'", ci.className())));
    }

    private static List<ClassInfo> processPath(ClassPool cp, Path path) {
        try {
            System.out.println("Processing path: " + path.toString() + " (is jar: " + path.toString().endsWith(".jar") + ")");
            if (path.toFile().isDirectory()) {
                return processDirectory(cp, path);
            } else if (path.toString().endsWith(".jar")) {
                return processJar(cp, path);
            } else {
                return Arrays.asList(processFile(cp, path));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<ClassInfo> processJar(ClassPool cp, Path path) throws Exception {
        return processZipStream(cp, new FileInputStream(path.toFile()));
    }

    private static List<ClassInfo> processZipStream(ClassPool cp, InputStream is) throws Exception {
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;
        List<ClassInfo> result = new ArrayList<>();
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                if (entry.getName().endsWith(".class")) {
                    result.add(processClass(cp, zis));
                } else if (entry.getName().endsWith(".jar")) {
                    processZipStream(cp, zis);
                }
            }
            zis.closeEntry();
        }
        return result;
    }

    private static List<ClassInfo> processDirectory(ClassPool cp, Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(f -> f.endsWith(".class") || f.toFile().isDirectory())
                    .flatMap(path -> processPath(cp, path).stream())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to process directory '" + directory.toString() + "'", e);
        }
    }

    private static ClassInfo processFile(ClassPool cp, Path path) throws Exception {
        try {
            return processClass(cp, new FileInputStream(path.toFile()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file '" + path.toString() + "'", e);
        }
    }

    private static ClassInfo processClass(ClassPool cp, InputStream source) throws Exception {
        BufferedInputStream bin = new BufferedInputStream(source);
        CtClass ctClass = cp.makeClass(bin);
        String name = ctClass.getName();
        System.out.println(String.format("Processing class %s", name));

        Arrays.stream(ctClass.getMethods())
                .filter(mi -> !mi.getDeclaringClass().getName().equals(Object.class.getCanonicalName()))
                .peek(mi -> System.out.println("Analyzing method: " + mi.getSignature()))
                .peek(BeanUploader::instrumentMethod)
                .filter(mi -> {
                    String methodName = mi.getName();
                    return methodName.startsWith("linkTo")
                            || methodName.startsWith("unlinkFrom")
                            || methodName.startsWith("linkChild")
                            || methodName.startsWith("unlinkChild")
                            || methodName.startsWith("update");
                })
                .forEach(mi -> {
                    List<String> arguments = getMethodArguments(mi.getMethodInfo().getDescriptor());
                    if (arguments.size() > 0) {
                        BeanMethod method = new BeanMethod(mi.getDeclaringClass().getName(), mi.getName(), arguments);
                        System.out.println("Upserting bean method " + mi.getSignature() + ": " + method.toJsonObject().toString());
                        metaCollection.upsert(method.getHash(), method.toJsonObject());
                    }
                });

        Couchbeans.SCOPE.collection(App.CLASS_COLLECTION_NAME)
                .upsert(name, ctClass.toBytecode(), UpsertOptions.upsertOptions().transcoder(RawBinaryTranscoder.INSTANCE));
        ClassInfo ci = new ClassInfo(ctClass);
        Couchbeans.store(ci);

        Class type = Class.forName(ci.className(), false, CouchbaseClassLoader.INSTANCE);

        try {
            if (ci.beanScope() == BeanScope.GLOBAL) {
                System.out.println("Creating singleton for bean " + ci.className());
                Singleton singleton = new Singleton(ci.className());
                if (Boolean.parseBoolean(Utils.envOrDefault("CBB_REINITIALIZE", "false").toLowerCase())) {
                    Couchbeans.OWNED.put(singleton, null);
                    Couchbeans.store(singleton);
                    System.out.println(String.format("Re-Initialized %s singleton of type %s", Couchbeans.key(singleton), ci.className()));
                } else if (Couchbeans.storeIfNotExists(singleton)) {
                    System.out.println(String.format("Auto-created %s singleton of type %s", Couchbeans.key(singleton), ci.className()));
                }
            } else if (ci.beanScope() == BeanScope.BUCKET || ci.beanScope() == BeanScope.NODE) {
                Utils.ensureCollectionExists(type);
            }
        } catch (Exception e) {
            RuntimeException re = new RuntimeException("Failed to initialize", e);
            BeanException.report(ci, re);
            re.printStackTrace();
        }
        return ci;
    }

    public static boolean interceptSetter(Object bean, String fieldName, Object value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetterWrapped(Object bean, String fieldName, Object... value) {
        if (Couchbeans.owned(bean)) {
            return false;
        }

        try {
            bean.getClass().getField(fieldName).set(bean, value[0]);
        } catch (Exception e) {
            BeanException.report(bean, e);
        }

        return true;
    }

    private static void instrumentMethod(CtMethod method) {
        BeanMethod.getSetterField(method).ifPresent(field -> {
                    instrumentMethod(method, "interceptSetter",
                            c -> c.append('"').append(field.getName()).append('"'));
                }
        );
    }

    private static void instrumentMethod(CtMethod method, String interceptorMethodName, Consumer<StringBuilder> argBuilder) {
        StringBuilder c = new StringBuilder();
        MethodInfo mi = method.getMethodInfo();
        LocalVariableAttribute vartable = (LocalVariableAttribute) mi.getCodeAttribute().getAttribute(LocalVariableAttribute.tag);

        try {
            c.append("if (cbb.BeanUploader.").append(interceptorMethodName).append("(this");
            if (argBuilder != null) {
                argBuilder.accept(c.append(", "));
            }
            for (int i = 0; i < method.getParameterTypes().length; i++) {
                String argName = vartable.variableName(i + 1);
                c.append(", ").append(argName);
            }
            c.append(")) { ");

            if (method.getReturnType() == CtClass.voidType) {
                c.append("return;");
            } else {
                c.append("return null;");
            }
            c.append(" }");

            String code = c.toString();

            LOGGER.info("Instrumenting method {} with code: \n {}", method.getLongName(), code);
            method.insertBefore(code);
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getMethodArguments(String signature) {
        signature = signature.substring(signature.indexOf("(") + 1);
        System.out.println("Parsing signature: " + signature);
        if (signature.lastIndexOf(")") == 0) {
            return Collections.EMPTY_LIST;
        }
        int n = Descriptor.numOfParameters(signature);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < signature.lastIndexOf(")"); ) {
            String name = toCtClassName(signature, i);
            i += name.length() + 2;
            result.add(name);
        }
        return result;
    }


    private static String toCtClassName(String desc, int i) {
        int i2;
        String name;

        int arrayDim = 0;
        char c = desc.charAt(i);
        while (c == '[') {
            ++arrayDim;
            c = desc.charAt(++i);
        }

        if (c == 'L') {
            i2 = desc.indexOf(';', ++i);
            return desc.substring(i, i2++).replace('/', '.');
        } else {
            CtClass type = toPrimitiveClass(c);
            if (type == null)
                throw new RuntimeException("Unknown type: " + c);
            else
                name = type.getName();
        }

        if (arrayDim > 0) {
            StringBuffer sbuf = new StringBuffer(name);
            while (arrayDim-- > 0)
                sbuf.append("[]");

            name = sbuf.toString();
        }

        return name;
    }

    static CtClass toPrimitiveClass(char c) {
        CtClass type = null;
        switch (c) {
            case 'Z':
                type = CtClass.booleanType;
                break;
            case 'C':
                type = CtClass.charType;
                break;
            case 'B':
                type = CtClass.byteType;
                break;
            case 'S':
                type = CtClass.shortType;
                break;
            case 'I':
                type = CtClass.intType;
                break;
            case 'J':
                type = CtClass.longType;
                break;
            case 'F':
                type = CtClass.floatType;
                break;
            case 'D':
                type = CtClass.doubleType;
                break;
            case 'V':
                type = CtClass.voidType;
                break;
        }

        return type;
    }

    public static boolean interceptSetter(Object bean, String fieldName, boolean value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, char value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, byte value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, short value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, int value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, long value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, float value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }

    public static boolean interceptSetter(Object bean, String fieldName, double value) {
        return interceptSetterWrapped(bean, fieldName, new Object[]{value});
    }
}