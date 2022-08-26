package com.couchbeans.gradle

import cbb.BeanUploader
import cbb.Utils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar

abstract class UploadBeansTask extends DefaultTask {
    private CouchbeansGradlePlugin plugin
    UploadBeansTask() {
        super()
        dependsOn(project.tasks.jar)
        plugin = project.plugins.apply(CouchbeansGradlePlugin)
    }

    @TaskAction
    def uploadCouchbeans() {
        CouchbeansExtension config = plugin.config
        Utils.envOverride("CBB_CLUSTER", config.getCluster().getOrElse("localhost"));
        Utils.envOverride("CBB_USERNAME", config.getUsername().getOrElse("Administrator"));
        Utils.envOverride("CBB_PASSWORD", config.getPassword().getOrElse("password"));
        Utils.envOverride("CBB_BUCKET", config.getBucket().getOrElse("default"));
        Utils.envOverride("CBB_SCOPE", config.getScope().getOrElse("_default"));

        Jar jarTask = project.tasks.jar;
        String[] jars = jarTask.outputs.files.files.stream().map(f -> f.toString()).toArray(String[]::new);
        BeanUploader.run(jars);
    }
}
