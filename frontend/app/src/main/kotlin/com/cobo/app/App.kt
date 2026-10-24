package com.cobo.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.data.CacheStore
import kotlinx.coroutines.flow.MutableSharedFlow
import com.cobo.app.data.Repo
import com.cobo.app.data.Settings
import com.cobo.app.data.Store

class CoboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }
}

object App {
    lateinit var context: Context
        private set
    lateinit var store: Store
        private set
    lateinit var repo: Repo
        private set
    lateinit var settings: Settings
        private set
    lateinit var cache: CacheStore
        private set

    var feedVersion by mutableIntStateOf(0)
        private set

    val feedRefreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val feedTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val messagesTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val followEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun notifyFollowChanged() {
        followEvents.tryEmit(Unit)
    }

    fun notifyPublished() {
        feedVersion++
    }

    fun requestFeedRefresh() {
        feedRefreshEvents.tryEmit(Unit)
    }

    fun requestFeedTop() {
        feedTopEvents.tryEmit(Unit)
    }

    fun requestMessagesTop() {
        messagesTopEvents.tryEmit(Unit)
    }

    fun toast(message: String) {
        if (message.isBlank()) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun init(context: Context) {
        if (this::store.isInitialized) return
        this.context = context.applicationContext
        store = Store(this.context)
        cache = CacheStore(this.context)
        repo = Repo(this.context, store)
        settings = Settings(this.context)
    }
}
