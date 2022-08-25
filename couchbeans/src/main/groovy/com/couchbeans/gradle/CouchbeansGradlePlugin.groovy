package com.couchbeans.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class CouchbeansGradlePlugin implements Plugin<Project> {
    private static final String JAR_VERSION = "0.0.1"
    CouchbeansExtension config
    @Override
    void apply(Project project) {
        System.out.println("Applying couchbeans plugin")
        project.tasks.compileJava.options.debug = true
        project.tasks.compileJava.options.debugOptions.debugLevel = "lines,vars"
        config = project.extensions.create("couchbeans", CouchbeansExtension)
        project.tasks.register("uploadCouchbeans", UploadBeansTask)
        project.dependencies.add("implementation", "com.couchbase:couchbeans:" + JAR_VERSION)
    }
}
