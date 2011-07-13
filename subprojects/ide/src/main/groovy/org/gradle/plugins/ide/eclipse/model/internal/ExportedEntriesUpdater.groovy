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

package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.plugins.ide.eclipse.model.AbstractLibrary
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry
import org.gradle.plugins.ide.eclipse.model.ProjectDependency

/**
 * @author: Szczepan Faber, created at: 7/4/11
 */
class ExportedEntriesUpdater {
    void updateExported(List<ClasspathEntry> classpathEntries, List<String> noExportConfigNames) {
        classpathEntries.each {
            if (it instanceof AbstractLibrary || it instanceof ProjectDependency) {
                if (noExportConfigNames.contains(it.declaredConfigurationName)) {
                    it.exported = false
                }
            }
        }
    }
}