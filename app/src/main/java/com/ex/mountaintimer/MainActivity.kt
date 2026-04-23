package com.ex.mountaintimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.ex.mountaintimer.ui.theme.MountainTimerTheme

/**
 * 主 Activity
 *
 * 畫面流程：
 * - MAP: 主地圖（含 HUD、自動計時）
 * - ROUTE_LIST: 路線列表
 * - ROUTE_CREATE: 新增路線
 * - HISTORY: 歷史紀錄列表
 * - HISTORY_DETAIL: 歷史紀錄詳情（軌跡回放）
 */
class MainActivity : ComponentActivity() {

    private enum class Screen {
        MAP,
        ROUTE_LIST,
        ROUTE_CREATE,
        HISTORY,
        HISTORY_DETAIL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MountainTimerTheme(dynamicColor = false) {
                var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
                var selectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }
                var selectedHistoryId by rememberSaveable { mutableStateOf<Long?>(null) }

                when (screen) {
                    Screen.MAP -> {
                        MapScreen(
                            selectedRouteId = selectedRouteId,
                            onOpenRouteList = { screen = Screen.ROUTE_LIST },
                            onOpenHistory = { screen = Screen.HISTORY }
                        )
                    }

                    Screen.ROUTE_LIST -> {
                        RouteListScreen(
                            onBack = { screen = Screen.MAP },
                            onPickRoute = { id ->
                                selectedRouteId = id
                                screen = Screen.MAP
                            },
                            onCreateRoute = { screen = Screen.ROUTE_CREATE }
                        )
                    }

                    Screen.ROUTE_CREATE -> {
                        RouteCreateScreen(
                            onBack = { screen = Screen.ROUTE_LIST },
                            onSaved = { screen = Screen.ROUTE_LIST }
                        )
                    }

                    Screen.HISTORY -> {
                        HistoryScreen(
                            onBack = { screen = Screen.MAP },
                            onOpenDetail = { runId ->
                                selectedHistoryId = runId
                                screen = Screen.HISTORY_DETAIL
                            }
                        )
                    }

                    Screen.HISTORY_DETAIL -> {
                        HistoryDetailScreen(
                            runId = selectedHistoryId ?: 0L,
                            onBack = { screen = Screen.HISTORY }
                        )
                    }
                }
            }
        }
    }
}
