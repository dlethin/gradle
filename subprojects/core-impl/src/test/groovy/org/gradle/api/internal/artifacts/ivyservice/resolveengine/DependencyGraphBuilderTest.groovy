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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveData
import org.apache.ivy.core.resolve.ResolveEngine
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.version.VersionMatcher
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ModuleDependency

import org.gradle.api.artifacts.ResolvedDependency

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor
import org.gradle.api.specs.Spec
import spock.lang.Specification
import org.gradle.api.internal.artifacts.ivyservice.*
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

class DependencyGraphBuilderTest extends Specification {
    final ModuleDescriptorConverter moduleDescriptorConverter = Mock()
    final ResolvedArtifactFactory resolvedArtifactFactory = Mock()
    final ConfigurationInternal configuration = Mock()
    final ResolveEngine resolveEngine = Mock()
    final ResolveData resolveData = new ResolveData(resolveEngine, new ResolveOptions())
    final ModuleConflictResolver conflictResolver = Mock()
    final DependencyToModuleResolver dependencyResolver = Mock()
    final ArtifactToFileResolver artifactResolver = Mock()
    final VersionMatcher versionMatcher = Mock()
    final DefaultModuleDescriptor root = revision('root')
    final DependencyGraphBuilder builder = new DependencyGraphBuilder(moduleDescriptorConverter, resolvedArtifactFactory, artifactResolver, dependencyResolver, conflictResolver)

    def setup() {
        config(root, 'root', 'default')
        _ * configuration.name >> 'root'
        _ * moduleDescriptorConverter.convert(_, _) >> root
        _ * resolvedArtifactFactory.create(_, _, _) >> { owner, artifact, resolver ->
            return new DefaultResolvedArtifact(owner, artifact, null)
        }
    }

    def "does not include evicted module when selected module already traversed before conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, selected
        traverses selected, c
        traverses root, b
        traverses b, d
        doesNotTraverse d, evicted // Conflict is deeper than all dependencies of selected module
        doesNotTraverse evicted, e

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.2', '1.1']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, c, d)
    }

    def "does not include evicted module when evicted module already traversed before conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses evicted, c
        traverses root, b
        traverses b, d
        traverses d, selected // Conflict is deeper than all dependencies of other module
        traverses selected, e

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, d, e)
    }

    def "does not include evicted module when path through evicted module is queued for traversal when conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        def e = revision('e')
        traverses root, evicted
        traverses evicted, c
        doesNotTraverse c, d
        traverses root, b
        traverses b, selected
        traverses selected, e

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, e)
    }

    def "does not include evicted module when another path through evicted module traversed after conflict detected"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        def b = revision('b')
        def c = revision('c')
        def d = revision('d')
        traverses root, evicted
        doesNotTraverse evicted, d
        traverses root, selected
        traverses selected, c
        traverses root, b
        doesNotTraverse b, evicted

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            assert candidates*.revision == ['1.1', '1.2']
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selected, b, c)
    }

    def "restarts conflict resolution when later conflict on same module discovered"() {
        given:
        def selectedA = revision('a', '1.2')
        def evictedA1 = revision('a', '1.1')
        def evictedA2 = revision('a', '1.0')
        def selectedB = revision('b', '2.2')
        def evictedB = revision('b', '2.1')
        def c = revision('c')
        traverses root, evictedA1
        traverses root, selectedA
        traverses selectedA, c
        traverses root, evictedB
        traverses root, selectedB
        doesNotTraverse selectedB, evictedA2

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select({it*.revision == ['1.1', '1.2']}, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }
        1 * conflictResolver.select({it*.revision == ['2.1', '2.2']}, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '2.2' }
        }
        1 * conflictResolver.select({it*.revision == ['1.1', '1.2', '1.0']}, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }
        0 * conflictResolver._

        and:
        modules(result) == ids(selectedA, c, selectedB)

    }

    def "does not attempt to resolve a dependency whose target module is excluded earlier in the path"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b, exclude: c
        doesNotTraverse b, c

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        modules(result) == ids(a, b)
    }

    def "does not include the artifacts of evicted modules"() {
        given:
        def selected = revision('a', '1.2')
        def evicted = revision('a', '1.1')
        traverses root, selected
        doesNotTraverse root, evicted

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        1 * conflictResolver.select(!null, !null) >> { Set<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root ->
            return candidates.find { it.revision == '1.2' }
        }

        and:
        artifacts(result) == ids(selected)
    }

    def "does not include the artifacts of excluded modules"() {
        given:
        def a = revision('a')
        def b = revision('b')
        def c = revision('c')
        traverses root, a
        traverses a, b, exclude: c
        doesNotTraverse b, c

        when:
        def result = builder.resolve(configuration, resolveData)

        then:
        artifacts(result) == ids(a, b)
    }

    def revision(String name, String revision = '1.0') {
        DefaultModuleDescriptor descriptor = new DefaultModuleDescriptor(new ModuleRevisionId(new ModuleId("group", name), revision), "release", new Date())
        config(descriptor, 'default')
        descriptor.addArtifact('default', new DefaultArtifact(descriptor.moduleRevisionId, new Date(), "art1", "art", "zip"))
        return descriptor
    }

    def config(DefaultModuleDescriptor descriptor, String name, String... extendsFrom) {
        def configuration = new Configuration(name, Configuration.Visibility.PUBLIC, null, extendsFrom, true, null)
        descriptor.addConfiguration(configuration)
        return configuration
    }

    def traverses(Map<String, ?> args = [:], DefaultModuleDescriptor from, DefaultModuleDescriptor to) {
        def resolver = dependsOn(args, from, to)
        1 * resolver.descriptor >> to
    }

    def doesNotTraverse(Map<String, ?> args = [:], DefaultModuleDescriptor from, DefaultModuleDescriptor to) {
        def resolver = dependsOn(args, from, to)
        0 * resolver.descriptor
    }

    def dependsOn(Map<String, ?> args = [:], DefaultModuleDescriptor from, DefaultModuleDescriptor to) {
        ModuleDependency moduleDependency = Mock()
        def dependencyId = args.revision ? new ModuleRevisionId(to.moduleRevisionId.moduleId, args.revision) : to.moduleRevisionId
        def descriptor = new EnhancedDependencyDescriptor(moduleDependency, from, dependencyId, false, false, true)
        descriptor.addDependencyConfiguration("default", "default")
        if (args.exclude) {
            descriptor.addExcludeRule("default", new DefaultExcludeRule(new ArtifactId(
                    args.exclude.moduleRevisionId.moduleId, PatternMatcher.ANY_EXPRESSION,
                    PatternMatcher.ANY_EXPRESSION,
                    PatternMatcher.ANY_EXPRESSION),
                    ExactPatternMatcher.INSTANCE, null))
        }
        from.addDependency(descriptor)

        ModuleVersionResolver resolver = Mock()
        (0..1) * dependencyResolver.create(descriptor) >> resolver
        _ * resolver.id >> to.moduleRevisionId
        return resolver
    }

    def ids(ModuleDescriptor... descriptors) {
        return descriptors.collect { new DefaultModuleVersionIdentifier(it.moduleRevisionId.organisation, it.moduleRevisionId.name, it.moduleRevisionId.revision)} as Set
    }

    def modules(LenientConfiguration config) {
        config.rethrowFailure()
        Set<ModuleVersionIdentifier> result = new LinkedHashSet<ModuleVersionIdentifier>()
        List<ResolvedDependency> queue = []
        queue.addAll(config.getFirstLevelModuleDependencies({true} as Spec))
        while (!queue.empty) {
            def node = queue.remove(0)
            result.add(node.module.id)
            queue.addAll(0, node.children)
        }
        return result
    }

    def artifacts(LenientConfiguration config) {
        return config.resolvedArtifacts.collect { it.moduleVersion.id } as Set
    }
}
