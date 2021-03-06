package com.github.blindpirate.gogradle.ide

import com.github.blindpirate.gogradle.GogradleRunner
import com.github.blindpirate.gogradle.support.WithResource
import com.github.blindpirate.gogradle.util.IOUtils
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModuleIml
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito

import static com.github.blindpirate.gogradle.task.GolangTaskContainer.*
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

@RunWith(GogradleRunner)
class GolangIdeaModuleTest {

    IdeaModule ideaModule = new IdeaModule(mock(Project), mock(IdeaModuleIml))

    GolangIdeaModule golangIdeaModule

    File resource

    @Mock
    TaskContainer taskContainer
    @Mock
    TaskInternal prepareTask
    @Mock
    TaskInternal resolveBuildDependenciesTask
    @Mock
    TaskInternal resolveTestDependenciesTask
    @Mock
    TaskInternal installBuildDependenciesTask
    @Mock
    TaskInternal installTestDependenciesTask


    @Before
    void setUp() {
        IdeaModule.class.fields.each {
            it.setAccessible(true)
            it.set(ideaModule, mock(it.type))
        }
        golangIdeaModule = new GolangIdeaModule(ideaModule)
        when(golangIdeaModule.getProject().getRootDir()).thenReturn(resource)

        when(ideaModule.getProject().getTasks()).thenReturn(taskContainer)
        when(taskContainer.getByName(PREPARE_TASK_NAME)).thenReturn(prepareTask)
        when(taskContainer.getByName(RESOLVE_BUILD_DEPENDENCIES_TASK_NAME)).thenReturn(resolveBuildDependenciesTask)
        when(taskContainer.getByName(RESOLVE_TEST_DEPENDENCIES_TASK_NAME)).thenReturn(resolveTestDependenciesTask)
        when(taskContainer.getByName(INSTALL_BUILD_DEPENDENCIES_TASK_NAME)).thenReturn(installBuildDependenciesTask)
        when(taskContainer.getByName(INSTALL_TEST_DEPENDENCIES_TASK_NAME)).thenReturn(installTestDependenciesTask)

    }

    @Test
    void 'all fields should be copied'() {
        assert IdeaModule.class.fields.every {
            it.setAccessible(true)
            it.get(golangIdeaModule) == it.get(ideaModule)
        }
    }

    @Test
    @WithResource('')
    void 'task should be executed in order'() {
        // given
        InOrder order = Mockito.inOrder(prepareTask,
                installBuildDependenciesTask,
                installTestDependenciesTask,
                resolveBuildDependenciesTask,
                resolveTestDependenciesTask)
        // when
        golangIdeaModule.resolveDependencies()
        // then
        order.verify(prepareTask).execute()
        order.verify(resolveBuildDependenciesTask).execute()
        order.verify(resolveTestDependenciesTask).execute()
        order.verify(installBuildDependenciesTask).execute()
        order.verify(installTestDependenciesTask).execute()
    }

    @Test
    @WithResource('')
    void 'goLibraries.xml should be generated'() {
        // when
        golangIdeaModule.resolveDependencies()
        // assert
        IOUtils.toString(new File(resource, '.idea/goLibraries.xml')).contains('project_gopath')
    }
}
