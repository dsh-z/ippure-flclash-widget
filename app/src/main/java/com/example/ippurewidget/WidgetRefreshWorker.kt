package com.example.ippurewidget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        IPPureWidget.isFetchingNow = false
        IPPureWidget().updateAll(applicationContext)
        return Result.success()
    }
}
