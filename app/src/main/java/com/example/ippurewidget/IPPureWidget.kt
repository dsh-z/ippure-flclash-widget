package com.example.ippurewidget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition

class IPPureWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val KEY_TRIGGER = longPreferencesKey("refresh_trigger")
        @Volatile var isFetchingNow: Boolean = false
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fallbackInfo = IPPureInfo("点击小组件测速", "等待首次检测", "", "", "0", "IPPure 节点纯净度监测", 0, -1, -1, false, false, "--:--", isFetchingNow)
        val (info, history) = if (isFetchingNow) {
            IPPureRepository.getCachedWithHistory(context, markRefreshing = true) ?: (fallbackInfo to emptyList())
        } else {
            IPPureRepository.fetchWithHistory(context) ?: (fallbackInfo to emptyList())
        }

        val dashboardBitmap = ChartBitmapRenderer.renderDashboard(info, history)

        provideContent {
            Box(modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<RefreshWidgetAction>())) {
                Image(
                    provider = ImageProvider(dashboardBitmap),
                    contentDescription = "IPPure FlClash Pro 监控看板",
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxSize()
                )
            }
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        IPPureWidget.isFetchingNow = true
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { this[IPPureWidget.KEY_TRIGGER] = System.currentTimeMillis() }
        }
        IPPureWidget().update(context, glanceId)

        IPPureWidget.isFetchingNow = false
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { this[IPPureWidget.KEY_TRIGGER] = System.currentTimeMillis() + 1L }
        }
        IPPureWidget().update(context, glanceId)
    }
}

class IPPureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IPPureWidget()
}
