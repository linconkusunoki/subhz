@file:OptIn(UnstableApi::class)

package com.subhz.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Gerenciador singleton do cache de áudio do Media3.
 *
 * Garante que a instância do [SimpleCache] seja única na aplicação para evitar
 * exceções de lock do banco de dados/diretório de cache ("Another SimpleCache instance already exists").
 *
 * @param context Contexto da aplicação Android.
 * @param cacheSizeMb Tamanho máximo do cache em Megabytes (padrão: 500 MB).
 */
class MediaCacheManager(
    private val context: Context,
    private val cacheSizeMb: Long = 500L
) {
    private val cacheDir = File(context.cacheDir, "audio_cache")

    /**
     * Instância singleton do [SimpleCache] do Media3.
     * Inicializado lazy de forma thread-safe.
     */
    val simpleCache: Cache by lazy {
        val databaseProvider = StandaloneDatabaseProvider(context)
        val evictor = LeastRecentlyUsedCacheEvictor(cacheSizeMb * 1024 * 1024)
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * Retorna o espaço atualmente ocupado pelo cache em bytes.
     */
    fun getCacheSize(): Long {
        return simpleCache.cacheSpace
    }

    /**
     * Limpa completamente todos os arquivos de mídia armazenados no cache.
     */
    fun clearCache() {
        simpleCache.keys.forEach { key ->
            simpleCache.removeResource(key)
        }
    }
}
