package com.github.blindpirate.gogradle.core.cache;

import com.github.blindpirate.gogradle.core.dependency.GolangDependency;

import java.nio.file.Path;
import java.util.concurrent.Callable;

// by default the global cache directory is located in ~/.gradle/go/
// + ~/.gradle/go
//   - gopath
//   - binary
public interface GlobalCacheManager {
    void ensureGlobalCacheExistAndWritable();

    Path getGlobalPackageCachePath(String packagePath);

    Path getGlobalGoBinCache(String relativePath);

    /**
     *
     * Locks global cache directory of {@code dependency}, and call the {@code callable}
     *
     * @param dependency dependency to be locked
     * @param callable code to be executed under lock
     * @param <T> return value type of callable
     * @return the return value of callable
     * @throws Exception exception thrown by callable
     */
    <T> T runWithGlobalCacheLock(GolangDependency dependency, Callable<T> callable) throws Exception;

    boolean currentDependencyIsOutOfDate();

    void updateCurrentDependencyLock();
}
