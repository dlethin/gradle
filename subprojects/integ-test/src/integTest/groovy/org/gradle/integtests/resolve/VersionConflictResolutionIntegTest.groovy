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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.junit.Test
import static org.hamcrest.Matchers.containsString

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class VersionConflictResolutionIntegTest extends AbstractIntegrationTest {
    @Test
    void "strict conflict resolution should fail due to conflict"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}

	configurations.compile.resolutionStrategy.failOnVersionConflict()
}
"""

        //when
        def result = executer.withTasks("tool:dependencies").runWithFailure()

        //then
        result.assertThatCause(containsString('A conflict was found between the following modules:'))
    }

    @Test
    void "strict conflict resolution should pass when no conflicts"() {
        repo.module("org", "foo", '1.3.3').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}

	configurations.all { resolutionStrategy.failOnVersionConflict() }
}
"""

        //when
        executer.withTasks("tool:dependencies").run()

        //then no exceptions are thrown
    }

    @Test
    void "strict conflict strategy can be used with forced modules"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()
        repo.module("org", "foo", '1.5.5').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
		compile('org:foo:1.5.5'){
		    force = true
		}
	}

	configurations.all { resolutionStrategy.failOnVersionConflict() }
}
"""

        //when
        executer.withTasks("tool:dependencies").run()

        //then no exceptions are thrown because we forced a certain version of conflicting dependency
    }

    @Test
    void "can force already resolved version of a module and avoid conflict"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {

	dependencies {
		compile project(':api')
		compile project(':impl')
	}
}

allprojects {
    configurations.all {
	    resolutionStrategy {
	        force 'org:foo:1.3.3'
	        failOnVersionConflict()
	    }
	}
}

"""

        //when
        executer.withTasks("api:dependencies", "tool:dependencies").run()

        //then no exceptions are thrown because we forced a certain version of conflicting dependency
    }

    @Test
    void "can force arbitrary version of a module and avoid conflict"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foobar", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()
        repo.module("org", "foo", '1.5.5').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
	group = 'org.foo.unittests'
	version = '1.0'
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
    task checkDeps(dependsOn: configurations.compile) << {
        assert configurations.compile*.name == ['api-1.0.jar', 'impl-1.0.jar', 'foo-1.5.5.jar']
        def metaData = configurations.compile.resolvedConfiguration
        def api = metaData.firstLevelModuleDependencies.find { it.moduleName == 'api' }
        assert api.children.size() == 1
        assert api.children.find { it.moduleName == 'foo' && it.moduleVersion == '1.5.5' }
        def impl = metaData.firstLevelModuleDependencies.find { it.moduleName == 'impl' }
        assert impl.children.size() == 1
        assert impl.children.find { it.moduleName == 'foo' && it.moduleVersion == '1.5.5' }
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            failOnVersionConflict()
            force 'org:foo:1.5.5'
        }
    }
}

"""

        // expect
        executer.withTasks(":tool:checkDeps").run()
    }

    @Test
    void "resolves to the latest version by default"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
    task checkDeps(dependsOn: configurations.compile) << {
        assert configurations.compile*.name == ['api.jar', 'impl.jar', 'foo-1.4.4.jar']
    }
}
"""

        //expect
        executer.withTasks("tool:checkDeps").run()
    }

    @Test
    void "latest strategy respects forced modules"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
	configurations.all {
	    resolutionStrategy {
	        failOnVersionConflict()
	        force 'org:foo:1.3.3'
	    }
	}
    task checkDeps(dependsOn: configurations.compile) << {
        assert configurations.compile*.name == ['api.jar', 'impl.jar', 'foo-1.3.3.jar']
    }
}
"""

        //expect
        executer.withTasks("tool:checkDeps").run()
    }

    @Test
    void "can force the version of a particular module"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def buildFile = file("build.gradle")
        buildFile << """
apply plugin: 'java'
repositories {
    maven { url "${repo.uri}" }
}

dependencies {
    compile 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'org:foo:1.4.4'
}

task checkDeps << {
    assert configurations.compile*.name == ['foo-1.4.4.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "can force the version of a direct dependency"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def buildFile = file("build.gradle")
        buildFile << """
apply plugin: 'java'
repositories {
    maven { url "${repo.uri}" }
}

dependencies {
    compile 'org:foo:1.4.4'
    compile ('org:foo:1.3.3') { force = true }
}

task checkDeps << {
    assert configurations.compile*.name == ['foo-1.3.3.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "forcing transitive dependency does not add extra dependency"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("hello", "world", '1.4.4').publish()

        def buildFile = file("build.gradle")
        buildFile << """
apply plugin: 'java'
repositories {
    maven { url "${repo.uri}" }
}

dependencies {
    compile 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'hello:world:1.4.4'
}

task checkDeps << {
    assert configurations.compile*.name == ['foo-1.3.3.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "does not attempt to resolve an evicted dependency"() {
        repo.module("org", "external", "1.2").publish()
        repo.module("org", "dep", "2.2").dependsOn("org", "external", "1.0").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url "${repo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps << {
    assert configurations.compile*.name == ['external-1.2.jar', 'dep-2.2.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "resolves dynamic dependency before resolving conflict"() {
        repo.module("org", "external", "1.2").publish()
        repo.module("org", "external", "1.4").publish()
        repo.module("org", "dep", "2.2").dependsOn("org", "external", "1.+").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url "${repo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps << {
    assert configurations.compile*.name == ['dep-2.2.jar', 'external-1.4.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    def getRepo() {
        return maven(file("repo"))
    }
}
