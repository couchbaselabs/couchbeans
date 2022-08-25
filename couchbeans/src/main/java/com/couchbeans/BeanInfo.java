package com.couchbeans;

import com.couchbase.client.java.json.JsonObject;
import com.google.common.collect.Streams;
import org.apache.tools.ant.util.StreamUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BeanInfo {
    private long revision;
    private Set<String> parentBeans;
    private String lastAppliedSource;

    public BeanInfo() {

    }

    protected BeanInfo(Object bean) {
        this.revision = 0;
    }

    public long revision() {
        return revision;
    }

    public void incrementRevision() {
        revision++;
    }
    protected void updateAppliedSource(String source) {
        lastAppliedSource = source;
    }

    public String lastAppliedSource() {
        return lastAppliedSource;
    }

    public List<String> detectChangedFields(String newSource) {
        Map<String, Object> oldJson = (lastAppliedSource == null) ? new HashMap<>() : JsonObject.fromJson(lastAppliedSource).toMap();
        Map<String, Object> newJson = JsonObject.fromJson(newSource).toMap();
        return Streams.concat(oldJson.keySet().stream(), newJson.keySet().stream())
                .distinct()
                .filter(key -> !Objects.equals(oldJson.get(key), newJson.get(key)))
                .collect(Collectors.toList());
    }
}
