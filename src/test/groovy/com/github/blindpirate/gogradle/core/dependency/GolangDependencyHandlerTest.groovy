package com.github.blindpirate.gogradle.core.dependency

import com.github.blindpirate.gogradle.GogradleRunner
import com.github.blindpirate.gogradle.core.dependency.parse.NotationParser
import com.github.blindpirate.gogradle.util.ReflectionUtils
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

@RunWith(GogradleRunner)
class GolangDependencyHandlerTest {

    @Mock
    ConfigurationContainer configurationContainer
    @Mock
    Configuration configuration
    @Mock
    AbstractGolangDependency dependency
    @Mock
    NotationParser notationParser
    @Mock
    DependencySet dependencies

    GolangDependencyHandler handler

    @Before
    void setUp() {
        handler = new GolangDependencyHandler(configurationContainer, notationParser)
        when(configurationContainer.findByName('build')).thenReturn(configuration)
        when(notationParser.parse('notation')).thenReturn(dependency)
        when(configuration.getDependencies()).thenReturn(dependencies)
    }

    @Test
    void 'unsupported method should all throw UnsupportedException'() {
        ReflectionUtils.testUnsupportedMethods(handler, DependencyHandler, ['create', 'add'])
    }

    @Test(expected = MissingMethodException)
    void 'exception should be thrown if no configuration found'() {
        handler.unexistent('')
    }

    @Test
    void 'adding configuration should succeed'() {
        handler.add('build', 'notation')
        verify(dependencies).add(dependency)
    }

    @Test
    void 'creating dependency should succeed'(){
        assert handler.create('notation').is(dependency)
    }
}
