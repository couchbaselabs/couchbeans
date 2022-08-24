package com.couchbeans.gradle

import org.gradle.api.provider.Property

interface CouchbeansExtension {
    Property<String> getCluster();
    Property<String> getUsername();
    Property<String> getPassword();
    Property<String> getBucket();
    Property<String> getScope();
}