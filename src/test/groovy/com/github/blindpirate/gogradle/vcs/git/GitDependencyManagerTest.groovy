package com.github.blindpirate.gogradle.vcs.git

import com.github.blindpirate.gogradle.GogradleRunner
import com.github.blindpirate.gogradle.core.GolangPackage
import com.github.blindpirate.gogradle.core.VcsGolangPackage
import com.github.blindpirate.gogradle.core.cache.GlobalCacheManager
import com.github.blindpirate.gogradle.core.dependency.*
import com.github.blindpirate.gogradle.core.dependency.produce.DependencyVisitor
import com.github.blindpirate.gogradle.core.dependency.produce.strategy.DependencyProduceStrategy
import com.github.blindpirate.gogradle.core.exceptions.DependencyInstallationException
import com.github.blindpirate.gogradle.core.exceptions.DependencyResolutionException
import com.github.blindpirate.gogradle.support.MockOffline
import com.github.blindpirate.gogradle.support.WithMockInjector
import com.github.blindpirate.gogradle.support.WithResource
import com.github.blindpirate.gogradle.util.DependencyUtils
import com.github.blindpirate.gogradle.util.IOUtils
import com.github.blindpirate.gogradle.util.ReflectionUtils
import com.github.blindpirate.gogradle.vcs.VcsType
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock

import java.nio.file.Paths
import java.util.concurrent.Callable

import static com.github.blindpirate.gogradle.core.dependency.resolve.AbstractVcsDependencyManagerTest.callCallableAnswer
import static com.github.blindpirate.gogradle.util.DependencyUtils.mockWithName
import static com.github.blindpirate.gogradle.vcs.git.GitDependencyManager.DEFAULT_BRANCH
import static java.util.Optional.empty
import static java.util.Optional.of
import static org.mockito.Matchers.any
import static org.mockito.Matchers.anyString
import static org.mockito.Mockito.*

@RunWith(GogradleRunner)
@WithResource('')
@WithMockInjector
class GitDependencyManagerTest {

    GitNotationDependency notationDependency = mockWithName(GitNotationDependency, 'github.com/a/b')
    GitResolvedDependency resolvedDependency = mockWithName(GitResolvedDependency, 'github.com/a/b')

    @Mock
    GlobalCacheManager cacheManager
    @Mock
    GitAccessor gitAccessor
    @Mock
    Repository repository
    @Mock
    GolangDependencySet dependencySet
    @Mock
    DependencyProduceStrategy strategy
    @Mock
    Set exclusionSpecs

    GitDependencyManager gitDependencyManager

    String commitId = '1' * 40
    RevCommit revCommit = RevCommitUtils.of(commitId, 123)
    String repoUrl = 'https://github.com/a/b.git'
    GolangPackage thePackage = VcsGolangPackage.builder()
            .withPath('github.com/a/b')
            .withRootPath('github.com/a/b')
            .withVcsType(VcsType.GIT)
            .withUrl(repoUrl)
            .build()

    File resource

    @Before
    void setUp() {
        gitDependencyManager = new GitDependencyManager(cacheManager,
                gitAccessor,
                mock(DependencyVisitor),
                mock(DependencyRegistry))

        when(cacheManager.runWithGlobalCacheLock(any(GolangDependency), any(Callable))).thenAnswer(callCallableAnswer)
        when(cacheManager.getGlobalPackageCachePath(anyString())).thenReturn(resource.toPath())
        when(gitAccessor.getRepository(resource)).thenReturn(repository)
        when(gitAccessor.hardResetAndPull(anyString(), any(Repository))).thenReturn(repository)
        when(gitAccessor.findCommit(repository, commitId)).thenReturn(of(revCommit))
        when(gitAccessor.headCommitOfBranch(repository, DEFAULT_BRANCH))
                .thenReturn(of(revCommit))

        when(gitAccessor.getRemoteUrl(repository)).thenReturn("https://github.com/a/b.git")
        when(notationDependency.getStrategy()).thenReturn(strategy)
        when(strategy.produce(any(ResolvedDependency), any(File), any(DependencyVisitor))).thenReturn(dependencySet)

        when(notationDependency.getTransitiveDepExclusions()).thenReturn(exclusionSpecs)

        when(notationDependency.getUrls()).thenReturn([repoUrl])
        when(notationDependency.getCommit()).thenReturn(revCommit.name)
        when(notationDependency.getPackage()).thenReturn(thePackage)
    }

    @Test
    void 'nonexistent repo should be cloned when url specified'() {
        // given:
        when(notationDependency.getUrls()).thenReturn(["url"])
        // when:
        gitDependencyManager.resolve(notationDependency)
        // then:
        verify(gitAccessor).cloneWithUrl('github.com/a/b', 'url', resource)
    }

    @Test
    void 'git notation dependency should be resolved successfully'() {
        // given
        when(notationDependency.getTag()).thenReturn('tag')
        when(notationDependency.isFirstLevel()).thenReturn(true)
        // when
        GitResolvedDependency result = gitDependencyManager.resolve(notationDependency)
        // then
        assertResolvedDependency(result)
    }

    void assertResolvedDependency(GitResolvedDependency result) {
        assert result.name == 'github.com/a/b'
        assert result.dependencies.isEmpty()
        assert result.repoUrl == repoUrl
        assert result.tag == 'tag'
        assert result.version == commitId
        assert result.updateTime == 123000L
        assert result.firstLevel
        assert ReflectionUtils.getField(result, 'transitiveDepExclusions').is(exclusionSpecs)
    }

    @Test
    void 'git notation dependency with non-root name should be resolved successfully'() {
        // given
        when(notationDependency.getName()).thenReturn('github.com/a/b/c')
        when(notationDependency.getPackage()).thenReturn(thePackage.resolve('github.com/a/b/c').get())
        when(notationDependency.getTag()).thenReturn('tag')
        when(notationDependency.isFirstLevel()).thenReturn(true)
        // when
        GitResolvedDependency result = gitDependencyManager.resolve(notationDependency)
        // then
        assertResolvedDependency(result)
    }

    @Test
    void 'update time of vendor dependency should be set to last commit time of that directory'() {
        // given
        VendorResolvedDependency vendorResolvedDependency = mockWithName(VendorResolvedDependency, 'vendorResolvedDependency')
        GolangDependencySet dependencies = DependencyUtils.asGolangDependencySet(vendorResolvedDependency)
        when(strategy.produce(any(ResolvedDependency), any(File), any(DependencyVisitor))).thenReturn(dependencies)
        when(vendorResolvedDependency.getHostDependency()).thenReturn(resolvedDependency)
        when(vendorResolvedDependency.getRelativePathToHost()).thenReturn(Paths.get('vendor/path/to/vendor'))
        when(vendorResolvedDependency.getDependencies()).thenReturn(GolangDependencySet.empty())
        when(gitAccessor.lastCommitTimeOfPath(repository, 'vendor/path/to/vendor')).thenReturn(456L)
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(vendorResolvedDependency).setUpdateTime(456L)
    }

    @Test
    void 'existed repository should be updated'() {
        IOUtils.write(resource, 'placeholder', '')
        // given:
        when(cacheManager.currentDependencyIsOutOfDate()).thenReturn(true)
        when(gitAccessor.getRemoteUrl(repository)).thenReturn(repoUrl)
        // when:
        gitDependencyManager.resolve(notationDependency)
        // then:
        verify(gitAccessor).hardResetAndPull('github.com/a/b', repository)
    }

    @Test
    @MockOffline
    void 'pull should not be executed if offline'() {
        IOUtils.write(resource, 'placeholder', '')
        // given:
        when(gitAccessor.getRemoteUrl(repository)).thenReturn(repoUrl)
        // when:
        gitDependencyManager.resolve(notationDependency)
        // then:
        verify(gitAccessor, times(0)).hardResetAndPull('github.com/a/b', repository)
    }

    @Test
    void 'dependency with tag should be resolved successfully'() {
        // given
        when(notationDependency.getTag()).thenReturn('tag')
        when(gitAccessor.findCommitByTag(repository, 'tag')).thenReturn(of(revCommit))
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(gitAccessor).checkout(repository, revCommit.getName())
    }

    @Test
    void 'tag should be interpreted as sem version if commit not found'() {
        // given
        when(notationDependency.getTag()).thenReturn('semversion')
        when(gitAccessor.findCommitByTag(repository, 'semversion')).thenReturn(empty())
        when(gitAccessor.findCommitBySemVersion(repository, 'semversion')).thenReturn(of(revCommit))
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(gitAccessor).checkout(repository, revCommit.getName())
    }

    @Test
    void 'commit will be searched if tag cannot be recognized'() {
        // given
        when(notationDependency.getCommit()).thenReturn(null)
        when(notationDependency.getTag()).thenReturn('tag')
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(gitAccessor).headCommitOfBranch(repository, 'master')
    }

    @Test
    void 'NEWEST_COMMIT should be recognized properly'() {
        // given
        when(notationDependency.getCommit()).thenReturn(GitNotationDependency.NEWEST_COMMIT)
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(gitAccessor).headCommitOfBranch(repository, 'master')
    }

    @Test(expected = DependencyResolutionException)
    void 'exception should be thrown when every url has been tried'() {
        // given
        when(gitAccessor.cloneWithUrl('github.com/a/b', repoUrl, resource)).thenThrow(new IllegalStateException())

        // when
        gitDependencyManager.resolve(notationDependency)
    }

    @Test
    void 'resetting to a commit should succeed'() {
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(gitAccessor).checkout(repository, revCommit.name)
    }

    @Test(expected = DependencyResolutionException)
    void 'trying to resolve an inexistent commit should result in an exception'() {
        // given
        when(notationDependency.getCommit()).thenReturn('inexistent')
        // when
        gitDependencyManager.resolve(notationDependency)
    }

    @Test(expected = DependencyResolutionException)
    void 'exception in locked block should not be swallowed'() {
        // given
        when(cacheManager.runWithGlobalCacheLock(any(GitNotationDependency), any(Callable)))
                .thenThrow(new IOException())
        // when
        gitDependencyManager.resolve(notationDependency)
    }

    @Test
    void 'mismatched repository should be cleared'() {
        // given
        when(notationDependency.getUrls()).thenReturn(['anotherUrl'])
        IOUtils.write(resource, 'some file', 'file content')
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        assert IOUtils.dirIsEmpty(resource)
    }

    @Test
    void 'installing a resolved dependency should succeed'() {
        // given
        File globalCache = IOUtils.mkdir(resource, 'globalCache')
        File projectGopath = IOUtils.mkdir(resource, 'projectGopath')
        when(cacheManager.getGlobalPackageCachePath(anyString())).thenReturn(globalCache.toPath())
        when(resolvedDependency.getVersion()).thenReturn(revCommit.getName())
        when(gitAccessor.getRepository(globalCache)).thenReturn(repository)
        // when
        gitDependencyManager.install(resolvedDependency, projectGopath)
        // then
        verify(gitAccessor).checkout(repository, revCommit.getName())
    }

    @Test(expected = DependencyInstallationException)
    void 'exception in install process should be wrapped'() {
        // given
        when(cacheManager.getGlobalPackageCachePath(anyString())).thenThrow(new IllegalStateException())
        // then
        gitDependencyManager.install(resolvedDependency, resource)
    }

    @Test
    void 'every url should be tried until success'() {
        // given
        when(notationDependency.getUrls()).thenReturn(['url1', 'url2'])
        when(gitAccessor.cloneWithUrl('github.com/a/b', 'url1', resource)).thenThrow(IOException)
        // when
        gitDependencyManager.resolve(notationDependency)
        // then
        verify(gitAccessor).cloneWithUrl('github.com/a/b', 'url2', resource)
    }

    @Test(expected = DependencyResolutionException)
    void 'exception should be thrown when all urls have been tried'() {
        // given
        when(notationDependency.getUrls()).thenReturn(['url1', 'url2'])
        when(gitAccessor.cloneWithUrl('github.com/a/b', 'url1', resource)).thenThrow(IOException)
        when(gitAccessor.cloneWithUrl('github.com/a/b', 'url2', resource)).thenThrow(IOException)
        // then
        gitDependencyManager.resolve(notationDependency)
    }

}
