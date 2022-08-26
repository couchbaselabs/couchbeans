package com.couchbeans.gradle

import org.apache.commons.lang3.exception.ExceptionUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.impldep.org.yaml.snakeyaml.tokens.ScalarToken

class CouchbeansGradlePlugin implements Plugin<Project> {
    private static final String JAR_VERSION = "0.0.1"
    CouchbeansExtension config
    Dependency couchbeans

    @Override
    void apply(Project project) {
        project.plugins.apply(JavaPlugin)
        System.out.println("Applying couchbeans plugin")
        config = project.extensions.create("couchbeans", CouchbeansExtension)
        project.tasks.register("uploadCouchbeans", UploadBeansTask)

        try {
            couchbeans = project.dependencies.add("implementation", "com.couchbase:couchbeans:" + JAR_VERSION)
        } catch (Exception e) {
            couchbeans = project.dependencies.add("compile", "com.couchbase:couchbeans:" + JAR_VERSION)
        }
    }
}
