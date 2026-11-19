package dev.zophop.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zophop.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val forwardingManager = ForwardingManager.getInstance(applicationContext)
            forwardingManager.processPendingQueue()

            val pendingMessages = AppDatabase.getInstance(applicationContext)
                .messageDao()
                .getPendingMessages()

            if (pendingMessages.isNotEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
