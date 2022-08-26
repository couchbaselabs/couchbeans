package cbb;

import cbb.annotations.Scope;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.query.QueryOptions;
import javassist.CtClass;

import java.util.stream.Stream;

public class ClassInfo {
    public static final String COLLECTION = Utils.collectionName(ClassInfo.class);
    private String className;

    private BeanScope scope;

    public ClassInfo() {

    }

    public ClassInfo(Class from) {
        this.className = from.getCanonicalName();
        this.scope = (from.isAnnotationPresent(Scope.class)) ? ((Scope) from.getAnnotation(Scope.class)).value() : BeanScope.NORMAL;
    }

    public ClassInfo(CtClass from) {
        try {
            this.className = from.getName();
            this.scope = (from.hasAnnotation(Scope.class)) ? ((Scope)from.getAnnotation(Scope.class)).value() : BeanScope.NORMAL;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void className(String name) {
        this.className = name;
    }

    public String className() {
        return className;
    }

    public void scope(BeanScope scope) {
        this.scope = scope;
    }

    public BeanScope scope() {
        return scope;
    }

    public String naturalKey() {
        return className;
    }

    public static ClassInfo get(Class from) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE `className` = $1", Utils.collectionRef(Utils.collectionName(ClassInfo.class))),
                QueryOptions.queryOptions().parameters(JsonArray.from(from.getCanonicalName()))
        ).rowsAs(ClassInfo.class).stream().findFirst().orElseGet(() -> {
            ClassInfo bg = new ClassInfo(from);
            Couchbeans.store(bg);
            return bg;
        });
    }
}
