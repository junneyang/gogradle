package com.github.blindpirate.gogradle.crossplatform

import com.github.blindpirate.gogradle.GogradleRunner
import com.github.blindpirate.gogradle.GolangPluginSetting
import com.github.blindpirate.gogradle.support.WithResource
import com.github.blindpirate.gogradle.core.cache.GlobalCacheManager
import com.github.blindpirate.gogradle.util.HttpUtils
import com.github.blindpirate.gogradle.util.IOUtils
import com.github.blindpirate.gogradle.util.ProcessUtils
import com.github.blindpirate.gogradle.util.ProcessUtils.ProcessResult
import com.github.blindpirate.gogradle.util.ProcessUtils.ProcessUtilsDelegate
import com.github.blindpirate.gogradle.util.ReflectionUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import java.nio.file.Files
import java.nio.file.Path

import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.*

@RunWith(GogradleRunner)
@WithResource('')
class DefaultGoBinaryManagerTest {
    @Mock
    GolangPluginSetting setting
    @Mock
    HttpUtils httpUtils
    @Mock
    GlobalCacheManager globalCacheManager
    @Mock
    ProcessUtilsDelegate processUtilsDelegate
    @Mock
    ProcessResult processResult

    File resource

    InputStream mockGoTarGz = getClass().classLoader.getResourceAsStream('mock-go-1.7.4' + Os.getHostOs().archiveExtension())

    DefaultGoBinaryManager manager

    @Before
    void setUp() {
        ReflectionUtils.setStaticFinalField(ProcessUtils, 'DELEGATE', processUtilsDelegate)
        Process process = mock(Process)
        when(setting.getGoExecutable()).thenReturn("go")
        when(processUtilsDelegate.run(['go', 'version'], null, null)).thenReturn(process)
        when(processUtilsDelegate.getResult(process)).thenReturn(processResult)
        turnOffMockGo()

        when(globalCacheManager.getGlobalGoBinCache(anyString())).thenAnswer(new Answer<Object>() {
            @Override
            Object answer(InvocationOnMock invocation) throws Throwable {
                return resource.toPath().resolve(invocation.getArgument(0))
            }
        })

        when(httpUtils.download(anyString(), any(Path))).thenAnswer(new Answer<Object>() {
            @Override
            Object answer(InvocationOnMock invocation) throws Throwable {
                Path filePath = invocation.getArgument(1)
                Files.copy(mockGoTarGz, filePath)
                return null
            }
        })
        manager = new DefaultGoBinaryManager(setting, globalCacheManager, httpUtils)
    }

    @After
    void cleanUp() {
        ReflectionUtils.setStaticFinalField(ProcessUtils, 'DELEGATE', new ProcessUtilsDelegate())
    }

    private turnOnMockGo() {
        when(processResult.getStdout()).thenReturn('go version go1.7.1 darwin/amd64')
    }

    private turnOffMockGo() {
        when(processResult.getStdout()).thenReturn('This is not a golang executable')
    }

    @Test
    void 'user-specified go binary should be ignored if it cannot be executed'() {
        // given
        when(processUtilsDelegate.run(['/unexistent/go', 'version'], null, null)).thenThrow(new IllegalStateException())
        when(setting.getGoExecutable()).thenReturn('/unexistent/go')
        // then
        'the newest stable version will be used if local binary not exist and no version specified'()
    }

    @Test
    void 'local go binary should be returned if it exists and no version specified'() {
        // given
        turnOnMockGo()
        // then
        assert manager.getBinaryPath() == 'go'
        assert manager.getGoVersion() == '1.7.1'
        assert manager.getGorootEnv() == null
    }

    @Test
    void 'local go binary should be returned if specified version is exactly local version'() {
        // given
        turnOnMockGo()
        when(setting.getGoVersion()).thenReturn('1.7.1')
        // then
        assert manager.getBinaryPath() == 'go'
        assert manager.getGoVersion() == '1.7.1'
        assert manager.getGorootEnv() == null
    }

    @Test
    void 'the newest stable version will be used if local binary not exist and no version specified'() {
        // given
        when(httpUtils.get(anyString())).thenReturn('1.7.4')
        // then
        assert manager.getBinaryPath() == resource.toPath().resolve("1.7.4/go/bin/go${Os.getHostOs().exeExtension()}").toString()
        assert manager.getGoVersion() == '1.7.4'
        assert manager.getGorootEnv() == resource.toPath().resolve('1.7.4/go').toString()
        verify(httpUtils).download(anyString(), any(Path))
    }

    @Test
    void 'go binary in global cache should be returned directly if it has already existed'() {
        // given
        when(httpUtils.get(anyString())).thenReturn('1.7.4')
        IOUtils.write(resource, "1.7.4/go/bin/go${Os.getHostOs().exeExtension()}", 'mock go binary')
        // then
        assert manager.getBinaryPath() == resource.toPath().resolve("1.7.4/go/bin/go${Os.getHostOs().exeExtension()}").toString()
        assert manager.getGoVersion() == '1.7.4'
        assert manager.getGorootEnv() == resource.toPath().resolve('1.7.4/go').toString()
        verify(httpUtils, times(0)).download(anyString(), any(Path))
    }

    @Test
    void 'go binary with specified version should be downloaded'() {
        // given
        when(setting.getGoVersion()).thenReturn("1.7.4")
        // then
        assert manager.getBinaryPath() == resource.toPath().resolve("1.7.4/go/bin/go${Os.getHostOs().exeExtension()}").toString()
        assert manager.getGoVersion() == '1.7.4'
        assert manager.getGorootEnv() == resource.toPath().resolve('1.7.4/go').toString()
        verify(httpUtils).download(anyString(), any(Path))
    }

    @Test(expected = IllegalStateException)
    void 'exception should be thrown when specified version is invalid'() {
        // given
        when(setting.getGoVersion()).thenReturn('999.999.999')
        when(httpUtils.download(anyString(), any(Path))).thenThrow(new IOException())
        // then
        manager.getBinaryPath()
    }

    @Test(expected = IllegalStateException)
    void 'exception should be thrown when download fails'() {
        // given
        when(httpUtils.get(anyString())).thenReturn('1.7.4')
        when(httpUtils.download(anyString(), any(Path))).thenThrow(new IOException())
        // then
        manager.getBinaryPath()
    }

    @Test(expected = IllegalStateException)
    void 'exception should be thrown when getting version fails'() {
        // given
        when(httpUtils.get(anyString())).thenThrow(new IOException())
        // then
        manager.getBinaryPath()
    }

}
