package com.zy.homeworklog

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zy.homeworklog.ui.AddEditScreen
import com.zy.homeworklog.ui.AppTheme
import com.zy.homeworklog.ui.C
import com.zy.homeworklog.ui.HistoryScreen
import com.zy.homeworklog.ui.HomeScreen
import com.zy.homeworklog.ui.RecurringScreen
import com.zy.homeworklog.ui.ReminderSettingsScreen
import com.zy.homeworklog.ui.SettingsScreen
import com.zy.homeworklog.ui.SubjectsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home",
                    modifier = Modifier.fillMaxSize().background(C.Background)) {
                    composable("home") { entry ->
                        HomeScreen(
                            onAdd = { nav.navigateFrom(entry, "add") },
                            onSettings = { nav.navigateFrom(entry, "settings") },
                            onEdit = { id -> nav.navigateFrom(entry, "edit/$id") },
                        )
                    }
                    composable("add") { entry ->
                        AddEditScreen(
                            recordId = 0L,
                            onReminders = { nav.navigateFrom(entry, "reminders") },
                            onDone = { nav.returnFrom(entry) },
                        )
                    }
                    composable(
                        "edit/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        AddEditScreen(
                            // NavType.LongType 在 Bundle 里存的是 Long，必须用 getLong 读；
                            // 用 getString 取不到值，会退化成「添加」页。
                            recordId = entry.arguments?.getLong("id", 0L) ?: 0L,
                            onReminders = { nav.navigateFrom(entry, "reminders") },
                            onDone = { nav.returnFrom(entry) },
                        )
                    }
                    composable("settings") { entry ->
                        SettingsScreen(
                            onBack = { nav.returnFrom(entry) },
                            onHistory = { nav.navigateFrom(entry, "history") },
                            onSubjects = { nav.navigateFrom(entry, "subjects") },
                            onRecurring = { nav.navigateFrom(entry, "recurring/0") },
                            onReminders = { nav.navigateFrom(entry, "reminders") },
                        )
                    }
                    composable("history") { entry -> HistoryScreen(onBack = { nav.returnFrom(entry) }) }
                    composable("subjects") { entry -> SubjectsScreen(onBack = { nav.returnFrom(entry) }) }
                    composable("reminders") { entry -> ReminderSettingsScreen(onBack = { nav.returnFrom(entry) }) }
                    composable(
                        "recurring/{subjectId}",
                        arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
                    ) { entry ->
                        RecurringScreen(
                            subjectId = entry.arguments?.getLong("subjectId", 0L) ?: 0L,
                            onBack = { nav.returnFrom(entry) },
                        )
                    }
                }
                // 在 NavHost 建立图之后注册；正常恢复的页面不受影响。
                DisposableEffect(nav) {
                    var attached = true
                    val checkHost = {
                        // 不在生命周期分发过程中直接改导航返回栈。
                        window.decorView.post {
                            if (attached && lifecycle.currentState == Lifecycle.State.RESUMED) {
                                Log.d("NavigationRecovery", "resume route=${nav.currentDestination?.route}")
                                nav.restoreEmptyHost()
                            }
                        }
                    }
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) checkHost()
                    }
                    lifecycle.addObserver(observer)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) checkHost()
                    onDispose {
                        attached = false
                        lifecycle.removeObserver(observer)
                    }
                }
            }
        }
    }
}

/** 忽略已离开页面排队中的点击。 */
internal fun NavHostController.navigateFrom(source: NavBackStackEntry, route: String) {
    if (currentBackStackEntry !== source || source.lifecycle.currentState != Lifecycle.State.RESUMED) return
    navigate(route) { launchSingleTop = true }
}

/** 保存可能在后台完成，因此用返回栈身份而非 RESUMED 状态判断能否收尾。 */
internal fun NavHostController.returnFrom(source: NavBackStackEntry) {
    if (currentBackStackEntry !== source || source.destination.route == "home") return
    if (previousBackStackEntry != null) {
        // A successful pop already has a previous destination; only a failed pop needs recovery.
        if (!popBackStack()) restoreEmptyHost()
    } else {
        navigate("home") {
            popUpTo(source.destination.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

/** 空返回栈兜底：只在确实为空时回到首页，不重置有效路由、ViewModel 或编辑草稿。 */
internal fun NavHostController.restoreEmptyHost() {
    if (currentBackStackEntry == null) {
        Log.w("NavigationRecovery", "Recovering an empty navigation host")
        navigate("home") { launchSingleTop = true }
    }
}
