package couchbeans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BeanContext {
    private HashMap<Class, Object> beans = new HashMap<>();
    private HashMap<String, Collection<Class>> identities = new HashMap<>();

    public BeanContext() {

    }

    public void add(Object bean) {
        Class beanClass = bean.getClass();
        if (beans.containsKey(beanClass)) {
            throw new IllegalArgumentException("Bean of type '" + beanClass.getCanonicalName() + "' has already been added to the context");
        }
        beans.put(beanClass, bean);

        Utils.inheritanceDescriptorChain(beanClass).forEach(identity -> {
            if (!identities.containsKey(identity)) {
                identities.put(identity, new ArrayList<>());
            }
            identities.get(identity).add(beanClass);
        });
    }

    public Set<String> identities() {
        return identities.keySet();
    }

    public List<List<Object>> resolveArguments(List<String> identities) {
        String identity = identities.remove(0);
        if (!this.identities.containsKey(identity)) {
            throw new IllegalArgumentException("Unresolvable identity: '" + identity + "'");
        }
        List<List<Object>> myCalls = this.identities.get(identity).stream()
                .map(beans::get)
                .map(Arrays::asList)
                .collect(Collectors.toList());

        List<List<Object>> result = new ArrayList<>();

        if (identities.size() > 0) {
            myCalls.forEach(call -> {
                resolveArguments(identities).forEach(childArgs -> {
                    LinkedList<Object> finalCall = new LinkedList<>();
                    finalCall.add(call);
                    finalCall.addAll(childArgs);
                    result.add(finalCall);
                });
            });
        }

        return result;
    }

    public void addAll(Collection all) {
        all.forEach(this::add);
    }
}
