/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.tracing.integration.jpms;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpmsModulePathIntegrationTests {

    @Test
    void shouldFindMicrometerTracingAsAutomaticModule() throws URISyntaxException {
        Path tracingPath = getPathForClass(Tracer.class);
        ModuleFinder finder = ModuleFinder.of(tracingPath);

        Optional<ModuleReference> moduleRef = finder.find("micrometer.tracing");
        assertThat(moduleRef).isPresent();

        ModuleDescriptor descriptor = moduleRef.get().descriptor();
        assertThat(descriptor.name()).isEqualTo("micrometer.tracing");
        assertThat(descriptor.isAutomatic()).isTrue();
    }

    @Test
    void shouldResolveTracingAndSpringAopOnModulePathWithoutSplitPackageError() throws URISyntaxException {
        Path tracingPath = getPathForClass(Tracer.class);
        Path springAopPath = getPathForClass(ProxyFactory.class);

        ModuleFinder finder = ModuleFinder.of(tracingPath, springAopPath);

        Configuration parentConfig = ModuleLayer.boot().configuration();
        Configuration resolvedConfig = parentConfig.resolve(finder, ModuleFinder.of(),
                Set.of("micrometer.tracing", "spring.aop"));

        assertThat(resolvedConfig.findModule("micrometer.tracing")).isPresent();
        assertThat(resolvedConfig.findModule("spring.aop")).isPresent();
    }

    @Test
    void shouldFailWhenBothSpringAopAndAopAllianceAreOnModulePathDueToSplitPackage() throws URISyntaxException {
        Path springAopPath = getPathForClass(ProxyFactory.class);
        Path aopAlliancePath = getAopAlliancePath();

        ModuleFinder finder = ModuleFinder.of(springAopPath, aopAlliancePath);

        Configuration parentConfig = ModuleLayer.boot().configuration();

        assertThatThrownBy(() -> parentConfig.resolve(finder, ModuleFinder.of(), Set.of("spring.aop", "aopalliance")))
            .isInstanceOf(ResolutionException.class)
            .hasMessageContaining("org.aopalliance.intercept");
    }

    @Test
    void shouldFailWhenTracingResolvesBothSpringAopAndTransitiveAopAllianceOnModulePath() throws URISyntaxException {
        // When aopalliance was a transitive API dependency, a modular application
        // requiring
        // micrometer.tracing and spring.aop would have all three on the module path.
        Path tracingPath = getPathForClass(Tracer.class);
        Path springAopPath = getPathForClass(ProxyFactory.class);
        Path aopAlliancePath = getAopAlliancePath();

        ModuleFinder finder = ModuleFinder.of(tracingPath, springAopPath, aopAlliancePath);

        Configuration parentConfig = ModuleLayer.boot().configuration();

        // Resolving tracing + spring.aop with aopalliance present fails due to split
        // package
        assertThatThrownBy(
                () -> parentConfig.resolve(finder, ModuleFinder.of(), Set.of("micrometer.tracing", "spring.aop")))
            .isInstanceOf(ResolutionException.class)
            .hasMessageContaining("org.aopalliance.intercept");
    }

    private Path getPathForClass(Class<?> clazz) throws URISyntaxException {
        return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private Path getAopAlliancePath() {
        String classPath = System.getProperty("java.class.path");
        for (String entry : classPath.split(File.pathSeparator)) {
            if (entry.contains("aopalliance") && !entry.contains("spring")) {
                return Paths.get(entry);
            }
        }
        throw new IllegalStateException("aopalliance jar not found on classpath");
    }

}
