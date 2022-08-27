package cbb;

import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.query.QueryOptions;
import javassist.CtClass;

public class ClassInfo {
    public static final String COLLECTION = Utils.collectionName(ClassInfo.class);
    private String className;

    private BeanScope beanScope;

    public ClassInfo() {

    }

    public ClassInfo(Class from) {
        this.className = from.getCanonicalName();
        this.beanScope = BeanScope.get(from);
    }

    public ClassInfo(CtClass from) {
        this.className = from.getName();
        this.beanScope = BeanScope.get(from);
    }

    public void className(String name) {
        this.className = name;
    }

    public String className() {
        return className;
    }

    public void beanScope(BeanScope scope) {
        this.beanScope = scope;
    }

    public BeanScope beanScope() {
        return beanScope;
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
