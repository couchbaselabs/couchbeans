package com.couchbeans.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class CouchbeansGradlePlugin implements Plugin<Project> {
    CouchbeansExtension config
    @Override
    void apply(Project project) {
        System.out.println("Applying couchbeans plugin")
        config = project.extensions.create("couchbeans", CouchbeansExtension)
        project.tasks.register("uploadCouchbeans", UploadBeansTask)
    }
}