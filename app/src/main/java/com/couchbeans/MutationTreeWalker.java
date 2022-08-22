package com.couchbeans;

import java.util.LinkedList;
import java.util.List;

public class MutationTreeWalker {
    private List context = new LinkedList();
    private int position = 0;

    public MutationTreeWalker(Object bean) {
        context.add(bean);
    }

    public void walk() {
        for(;;position++) {
            if (position > context.size()) {
                break;
            }

            processBean(context.get(position));
        }
    }

    private void processBean(Object bean) {
        List<BeanLink> children = Utils.findLinkedBeans(bean);
        children.stream()
                .map(BeanLink::target)
                .forEach(childLink -> {

                });
    }
}
