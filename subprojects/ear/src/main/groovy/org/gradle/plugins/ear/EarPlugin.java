/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugins.ear;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;

import java.util.concurrent.Callable;

/**
 * <p>
 * A {@link Plugin} with tasks which assemble a web application into a EAR file.
 * </p>
 * 
 * @author David Gileadi, Hans Dockter
 */
public class EarPlugin implements Plugin<ProjectInternal> {

    public static final String EAR_TASK_NAME = "ear";

    public static final String DEPLOY_CONFIGURATION_NAME = "deploy";
    public static final String EARLIB_CONFIGURATION_NAME = "earlib";

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(BasePlugin.class);

        final EarPluginConvention pluginConvention = project.getServices().get(Instantiator.class).newInstance(EarPluginConvention.class, project.getFileResolver());
        project.getConvention().getPlugins().put("ear", pluginConvention);
        pluginConvention.setLibDirName("lib");
        pluginConvention.setAppDirName("src/main/application");

        wireEarTaskConventions(project, pluginConvention);
        configureConfigurations(project);

        //TODO evaluation order-sensitive
        final JavaPluginConvention javaPlugin = project.getConvention().findPlugin(JavaPluginConvention.class);
        if (javaPlugin != null) {
            SourceSet sourceSet = javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            sourceSet.getResources().srcDir(new Callable() {
                public Object call() throws Exception {
                    return pluginConvention.getAppDirName();
                }
            });
        }

        project.getTasks().withType(Ear.class, new Action<Ear>() {

            public void execute(final Ear task) {

                task.dependsOn(new Callable<FileCollection>() {

                    public FileCollection call() throws Exception {

                        if (javaPlugin != null) {
                            return javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                                    .getRuntimeClasspath();
                        }
                        return null;
                    }
                });
                task.from(new Callable<FileCollection>() {

                    public FileCollection call() throws Exception {

                        FileCollection files;
                        // add the app dir or the main java sourceSet
                        if (javaPlugin == null) {
                            files = project.fileTree(pluginConvention.getAppDirName());
                        } else {
                            files = javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
                        }
                        // add the module configuration's files
                        return files.plus(project.getConfigurations().getByName(DEPLOY_CONFIGURATION_NAME));
                    }
                });
                task.getLib().from(new Callable<FileCollection>() {

                    public FileCollection call() throws Exception {

                        return project.getConfigurations().getByName(EARLIB_CONFIGURATION_NAME);
                    }
                });
            }
        });

        Ear ear = project.getTasks().add(EAR_TASK_NAME, Ear.class);
        ear.setDescription("Generates a ear archive with all the modules, the application descriptor and the libraries.");
        DeploymentDescriptor deploymentDescriptor = pluginConvention.getDeploymentDescriptor();
        if (deploymentDescriptor != null) {
            if (deploymentDescriptor.getDisplayName() == null) {
                deploymentDescriptor.setDisplayName(project.getName());
            }
            if (deploymentDescriptor.getDescription() == null) {
                deploymentDescriptor.setDescription(project.getDescription());
            }
        }
        ear.setGroup(BasePlugin.BUILD_GROUP);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(new ArchivePublishArtifact(ear));
    }

    private void wireEarTaskConventions(Project project, final EarPluginConvention earConvention) {
        project.getTasks().withType(Ear.class, new Action<Ear>() {
            public void execute(Ear task) {
                task.getConventionMapping().map("libDirName", new Callable<String>() {
                    public String call() throws Exception { return earConvention.getLibDirName(); }
                });
                task.getConventionMapping().map("deploymentDescriptor", new Callable<DeploymentDescriptor>() {
                    public DeploymentDescriptor call() throws Exception { return earConvention.getDeploymentDescriptor(); }
                });
            }
        });
    }
    
    private void configureConfigurations(final Project project) {

        ConfigurationContainer configurations = project.getConfigurations();
        Configuration moduleConfiguration = configurations.add(DEPLOY_CONFIGURATION_NAME).setVisible(false)
                .setTransitive(false).setDescription("Classpath for deployable modules, not transitive.");
        Configuration earlibConfiguration = configurations.add(EARLIB_CONFIGURATION_NAME).setVisible(false)
                .setDescription("Classpath for module dependencies.");

        configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
                .extendsFrom(moduleConfiguration, earlibConfiguration);
    }
}
