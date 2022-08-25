package com.couchbeans;

import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbeans.annotations.External;
import com.couchbeans.annotations.Global;
import com.couchbeans.annotations.Local;
import javassist.CtClass;

public class ClassInfo {
    public static final String COLLECTION = Utils.collectionName(ClassInfo.class);
    private String className;

    private BeanType beanType;

    public ClassInfo() {

    }

    public ClassInfo(Class from) {
        this.className = from.getCanonicalName();
        this.beanType =
                from.isAnnotationPresent(External.class) ? BeanType.EXTERNAL :
                        from.isAnnotationPresent(Global.class) ? BeanType.GLOBAL :
                                from.isAnnotationPresent(Local.class) ? BeanType.LOCAL : BeanType.NORMAL;
    }

    public ClassInfo(CtClass from) {
        this.className = from.getName();
        this.beanType =
                from.hasAnnotation(External.class) ? BeanType.EXTERNAL :
                        from.hasAnnotation(Global.class) ? BeanType.GLOBAL :
                                from.hasAnnotation(Local.class) ? BeanType.LOCAL : BeanType.NORMAL;
    }

    public void className(String name) {
        this.className = name;
    }

    public String className() {
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
