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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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

    private static Set<String> classesToMethodIndex = new HashSet<>();

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
        Utils.createCollectionIfNotExists(App.RESOURCE_COLLECTION);
        Utils.createPrimaryIndexIfNotExists(App.RESOURCE_COLLECTION);
        Utils.ensureCollectionExists(BeanMethod.class);
        Utils.ensureCollectionExists(ClassInfo.class);
        Utils.ensureCollectionExists(BeanLink.class);
        Utils.ensureCollectionExists(Singleton.class);
    }

    private static void processPaths(String[] sources) {
        if (sources.length == 0) {
            sources = new String[]{System.getProperty("user.dir")};
        }
        ClassPool cp = new ClassPool();
        cp.insertClassPath(new ClassClassPath(BeanUploader.class));
        Arrays.stream(sources)
                .map(Path::of)
                .forEach(path -> processPath(cp, path));
    }

    private static void processPath(ClassPool cp, Path path) {
        try {
            System.out.println("Processing path: " + path.toString() + " (is jar: " + path.toString().endsWith(".jar") + ")");
            if (path.toFile().isDirectory()) {
                processDirectory(cp, path);
            } else if (path.toString().endsWith(".jar")) {
                processJar(cp, path);
            } else {
                processFile(cp, path);
            }
        } catch (ClassNotFoundException cnfe) {
            DEFERRED_PATHS.add(path.toString());
            return Collections.EMPTY_LIST;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processJar(ClassPool cp, Path path) throws Exception {
        processZipStream(cp, new FileInputStream(path.toFile()));
    }

    private static final String[] IGNORE_ZIP_PATHS = new String[]{
            "META-INF/.*"
    };

    private static void processZipStream(ClassPool cp, InputStream is) throws Exception {
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            System.out.println(String.format("Processing zip entry: %s", entry.getName()));
            if (!entry.isDirectory() && Arrays.stream(IGNORE_ZIP_PATHS).noneMatch(entry.getName()::matches)) {
                if (entry.getName().endsWith(".class")) {
                    processClass(cp, zis);
                } else if (entry.getName().endsWith(".jar")) {
                    processZipStream(cp, zis);
                } else {
                    processResource(entry.getName(), zis);
                }
            }
            zis.closeEntry();
        }
    }

    private static void processResource(String name, ZipInputStream zis) throws IOException {
        final BufferedInputStream bin = new BufferedInputStream(zis);
        Resource resource = new Resource(name, bin.readAllBytes());
        Couchbeans.SCOPE.collection(App.RESOURCE_COLLECTION).upsert(Utils.toId(name), resource);
    }

    private static void processDirectory(ClassPool cp, Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(f -> f.endsWith(".class") || f.toFile().isDirectory())
                    .forEach(path -> processPath(cp, path));
        } catch (Exception e) {
            throw new RuntimeException("Failed to process directory '" + directory.toString() + "'", e);
        }
    }

    private static void processFile(ClassPool cp, Path path) throws Exception {
        try {
            processClass(cp, new FileInputStream(path.toFile()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file '" + path.toString() + "'", e);
        }
    }

    private static final String[] EXCLUDE_PACKAGES = new String[]{
    };

    private static void processClass(ClassPool cp, InputStream source) throws Exception {
        final BufferedInputStream bin = new BufferedInputStream(source);
        final CtClass ctClass = cp.makeClass(bin);
        final String className = ctClass.getName();

        if (Arrays.stream(EXCLUDE_PACKAGES).noneMatch(className::startsWith)) {
            addClass(cp, ctClass);
        }
    }

    private static void addClass(ClassPool cp, CtClass ctClass) throws Exception {
        String name = ctClass.getName();
        System.out.println(String.format("Adding class %s", name));

        indexClassMethods(ctClass);

        Couchbeans.SCOPE.collection(App.CLASS_COLLECTION_NAME)
                .upsert(name, ctClass.toBytecode(), UpsertOptions.upsertOptions().transcoder(RawBinaryTranscoder.INSTANCE));
        ClassInfo ci = new ClassInfo(ctClass);

        try {
            if (ci.beanScope() == BeanScope.GLOBAL) {
                Couchbeans.store(ci);
                System.out.println("Creating singleton for bean " + ci.className());
                Singleton singleton = new Singleton(ci.className());
                if (Boolean.parseBoolean(Utils.envOrDefault("CBB_REINITIALIZE", "false").toLowerCase())) {
                    Couchbeans.OWNED.put(singleton, null);
                    Couchbeans.store(singleton);
                    System.out.println(String.format("Re-Initialized %s singleton of type %s", Couchbeans.key(singleton), ci.className()));
                } else if (Couchbeans.storeIfNotExists(singleton)) {
                    System.out.println(String.format("Auto-created %s singleton of type %s", Couchbeans.key(singleton), ci.className()));
                }
            }
        } catch (Exception e) {
            RuntimeException re = new RuntimeException("Failed to initialize", e);
            BeanException.report(ci, re);
            re.printStackTrace();
        }
    }

    private static void scheduleMethodIndex(CtClass ctClass) {
        System.out.println(String.format("Scheduled method indexing for class %s", ctClass.getName()));
        classesToMethodIndex.add(ctClass.getName());
    }

    private static void indexClassMethods(CtClass ctClass) {
        System.out.println(String.format("Indexing methods for class %s", ctClass.getName()));
        Arrays.stream(ctClass.getMethods())
                .filter(mi -> !mi.getDeclaringClass().getName().equals(Object.class.getCanonicalName()))
                .peek(mi -> System.out.println("Analyzing method: " + mi.getSignature()))
                .filter(mi -> {
                    String methodName = mi.getName();
                    return methodName.startsWith("linkTo")
                            || methodName.startsWith("unlinkFrom")
                            || methodName.startsWith("linkChild")
                            || methodName.startsWith("unlinkChild")
                            || methodName.startsWith("update");
                })
                .peek(BeanUploader::instrumentMethod)
                .forEach(mi -> {
                    try {
                        List<String> arguments = getMethodArguments(mi.getMethodInfo().getDescriptor());
                        if (arguments.size() > 0) {
                            BeanMethod method = new BeanMethod(mi.getDeclaringClass().getName(), mi.getName(), arguments);
                            System.out.println("Upserting bean method " + mi.getSignature() + ": " + method.toJsonObject().toString());
                            metaCollection.upsert(method.getHash(), method.toJsonObject());
                        }
                    } catch (Exception e) {
                        System.out.println(String.format("Failed to index method %s", mi.getSignature()));
                    }
                });
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