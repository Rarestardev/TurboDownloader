package com.rarestardev.turbodownloader.core

import android.util.Log
import com.rarestardev.turbodownloader.utils.TurboConstants
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.settings_pack

/**
 * Singleton engine for managing the libtorrent session.
 * Ensures only one session exists per process to prevent native crashes and port conflicts.
 * It handles global settings such as DHT, LSD, and memory limits.
 */
internal object TorrentEngine {
    private var _sessionManager: SessionManager? = null
    
    /**
     * Lazy-initialized libtorrent [SessionManager].
     */
    val sessionManager: SessionManager
        get() = _sessionManager ?: synchronized(this) {
            _sessionManager ?: SessionManager().also { _sessionManager = it }
        }

    /**
     * Reflects the current initialization state of the engine.
     */
    var isInitialized: Boolean = false
        private set

    /**
     * Starts the libtorrent session with optimized settings for Android (Low Memory Profile).
     */
    fun initialize() {
        if (isInitialized) return
        
        try {
            Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "Initializing TorrentEngine Singleton with Aggressive Memory Optimizations...")
            val settings = SettingsPack()
            
            // --- Basic Connectivity ---
            settings.setEnableDht(true)
            settings.setEnableLsd(true)
            
            // Safety: Temporarily disable UPnP/NAT-PMP to rule out native crashes
            settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
            settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
            
            // --- Memory & Resource Optimization ---
            // 1. Limit active connections and peer lists to save RAM
            settings.setInteger(settings_pack.int_types.connections_limit.swigValue(), 40)
            settings.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), 100)
            settings.setInteger(settings_pack.int_types.active_downloads.swigValue(), 3)
            settings.setInteger(settings_pack.int_types.active_limit.swigValue(), 10)
            
            // 2. Disk I/O & Cache Optimization
            // Enable OS cache to avoid allocating large buffers in the app's heap
            settings.setInteger(settings_pack.int_types.disk_io_write_mode.swigValue(), settings_pack.io_buffer_mode_t.enable_os_cache.swigValue())
            settings.setInteger(settings_pack.int_types.disk_io_read_mode.swigValue(), settings_pack.io_buffer_mode_t.enable_os_cache.swigValue())
            
            // Limit queued disk bytes to 512KB (very aggressive)
            settings.setInteger(settings_pack.int_types.max_queued_disk_bytes.swigValue(), 512 * 1024 / 16 / 1024) 
            
            // 3. DHT Optimization (Lower memory for routing tables)
            settings.setInteger(settings_pack.int_types.dht_max_peers.swigValue(), 100)
            
            settings.setDhtBootstrapNodes("router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,router.bitcomet.com:6881,dht.aelitis.com:6881")
            
            sessionManager.start(SessionParams(settings))
            isInitialized = true
            Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "TorrentEngine initialized successfully with Strict Low-Memory Profile.")
        } catch (e: Throwable) {
            Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Failed to initialize TorrentEngine", e)
        }
    }

    /**
     * Safely stops the session and releases native resources.
     */
    fun stop() {
        if (isInitialized) {
            try {
                sessionManager.stop()
                _sessionManager = null
                isInitialized = false
                Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "TorrentEngine stopped.")
            } catch (e: Throwable) {
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error stopping TorrentEngine", e)
            }
        }
    }
}
