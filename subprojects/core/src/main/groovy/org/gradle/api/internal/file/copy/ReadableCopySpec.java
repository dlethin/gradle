/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;

import java.util.Collection;

public interface ReadableCopySpec {
    RelativePath getDestPath();

    int getFileMode();

    int getDirMode();

    FileTree getSource();

    Collection<? extends ReadableCopySpec> getAllSpecs();

    boolean hasSource();

    Collection<? extends Action<? super FileCopyDetails>> getAllCopyActions();

    boolean getIncludeEmptyDirs();
}
