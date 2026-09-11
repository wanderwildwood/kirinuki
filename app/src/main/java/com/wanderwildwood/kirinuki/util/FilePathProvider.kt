package com.wanderwildwood.kirinuki.util

import java.io.File

/**
 * An interface providing the paths to relevant directories to place files
 */
interface FilePathProvider {
    val cacheDir: File
    val filesDir: File

    /**
     * This is where articles regular text should be placed
     */
    val articleDir: File

    /**
     * This is where the full text of articles should be placed
     */
    val fullArticleDir: File

    /**
     * Where http cache should reside
     */
    val httpCacheDir: File

    /**
     * Where images should be cached to
     */

    /**
     * Where images should be cached to for okhttp
     */
}

private class FilePathProviderImpl(
    override val cacheDir: File,
    override val filesDir: File,
) : FilePathProvider {
    override val articleDir: File = filesDir.resolve("articles")
    override val fullArticleDir: File = cacheDir.resolve("full_articles")
    override val httpCacheDir: File = cacheDir.resolve("http")
}

fun filePathProvider(
    cacheDir: File,
    filesDir: File,
): FilePathProvider = FilePathProviderImpl(cacheDir = cacheDir, filesDir = filesDir)
