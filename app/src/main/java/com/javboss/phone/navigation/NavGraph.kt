package com.javboss.phone.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.javboss.phone.JavBossApp
import com.javboss.phone.ui.detail.DetailScreen
import com.javboss.phone.ui.favorites.FavGroupScreen
import com.javboss.phone.ui.javlist.JavListScreen
import com.javboss.phone.ui.login.LoginScreen
import com.javboss.phone.ui.main.MainScreen
import com.javboss.phone.ui.player.PlayerScreen
import kotlinx.coroutines.launch

/**
 * 页面转场。
 * Navigation Compose 的默认转场是 `fadeIn/fadeOut(tween(700))`——700ms 的淡入淡出在手机上手感
 * 明显「慢半拍」，点封面进详情会像是卡了一下才跳。这里统一压到 200ms，
 * 位移只做 1/12 屏宽的轻推（不整屏滑动，避免大图和小屏来回甩），进出都跟手。
 */
private const val TRANSITION_MS = 200

/** 位移幅度：屏宽 / 12（约 8%） */
private const val SLIDE_DIVISOR = 12

private val slideSpec = tween<IntOffset>(
    durationMillis = TRANSITION_MS,
    easing = LinearOutSlowInEasing,
)
private val fadeSpec = tween<Float>(durationMillis = TRANSITION_MS, easing = LinearOutSlowInEasing)

/** 进入新页：从右侧轻推进入 + 淡入 */
private val navEnter: EnterTransition =
    slideInHorizontally(slideSpec) { it / SLIDE_DIVISOR } + fadeIn(fadeSpec)

/** 旧页退出：向左轻推 + 淡出 */
private val navExit: ExitTransition =
    slideOutHorizontally(slideSpec) { -it / SLIDE_DIVISOR } + fadeOut(fadeSpec)

/** 返回时新页从左侧轻推进入 */
private val navPopEnter: EnterTransition =
    slideInHorizontally(slideSpec) { -it / SLIDE_DIVISOR } + fadeIn(fadeSpec)

/** 返回时旧页向右轻推退出 */
private val navPopExit: ExitTransition =
    slideOutHorizontally(slideSpec) { it / SLIDE_DIVISOR } + fadeOut(fadeSpec)

/** 播放页要转屏，滑动会跟转屏动画打架，只用短淡入淡出 */
private val playerFadeEnter: EnterTransition = fadeIn(tween(150))
private val playerFadeExit: ExitTransition = fadeOut(tween(150))

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val DETAIL = "detail/{videoId}?code={code}"
    const val PLAYER = "player/{videoId}"
    const val JAVLIST =
        "javlist?title={title}&idolIds={idolIds}&tagIds={tagIds}&studioId={studioId}&seriesId={seriesId}"
    const val FAVGROUP = "favgroup/{groupId}?name={name}"

    /** videoId <= 0 表示纯元数据详情（作品没有关联视频），此时必须带 code */
    fun detail(videoId: Long, code: String? = null) =
        "detail/$videoId" + (code?.takeIf { it.isNotBlank() }
            ?.let { "?code=${android.net.Uri.encode(it)}" } ?: "")

    fun player(videoId: Long) = "player/$videoId"

    fun javList(
        title: String,
        idolIds: String = "",
        tagIds: String = "",
        studioId: Long = 0,
        seriesId: Long = 0,
    ) = "javlist?title=${android.net.Uri.encode(title)}" +
        "&idolIds=$idolIds&tagIds=$tagIds&studioId=$studioId&seriesId=$seriesId"

    fun favGroup(groupId: Long, name: String) =
        "favgroup/$groupId?name=${android.net.Uri.encode(name)}"
}

@Composable
fun JavBossNavGraph(startWithSession: Boolean) {
    val navController = rememberNavController()

    val goLogin = {
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }

    // 主动登出：本地先行（立即清会话回登录页，不被网络阻塞）；
    // 服务端登出后台异步通知，需在清 Cookie 前发起才能带上会话
    val app = LocalContext.current.applicationContext as JavBossApp
    val scope = rememberCoroutineScope()
    val doLogout: () -> Unit = {
        scope.launch {
            launch { app.container.authRepository.notifyServerLogout() }
            app.container.authRepository.clearLocalSession()
            goLogin()
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (startWithSession) Routes.MAIN else Routes.LOGIN,
        enterTransition = { navEnter },
        exitTransition = { navExit },
        popEnterTransition = { navPopEnter },
        popExitTransition = { navPopExit },
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                startTab = app.container.settings.launchTab.index,
                onOpenDetail = { videoId, code -> navController.navigate(Routes.detail(videoId, code)) },
                onPlay = { videoId -> navController.navigate(Routes.player(videoId)) },
                onOpenJavList = { title, idolIds, tagIds, studioId, seriesId ->
                    navController.navigate(Routes.javList(title, idolIds, tagIds, studioId, seriesId))
                },
                onFavGroupClick = { id, name -> navController.navigate(Routes.favGroup(id, name)) },
                onSessionExpired = goLogin,
                onLogout = doLogout,
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("videoId") { type = NavType.LongType },
                navArgument("code") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val videoId = entry.arguments?.getLong("videoId") ?: return@composable
            DetailScreen(
                videoId = videoId,
                javCode = entry.arguments?.getString("code"),
                onPlay = { navController.navigate(Routes.player(videoId)) },
                onOpenJavList = { title, idolIds, tagIds, studioId, seriesId ->
                    navController.navigate(Routes.javList(title, idolIds, tagIds, studioId, seriesId))
                },
                onBack = { navController.popBackStack() },
                onSessionExpired = goLogin,
            )
        }
        composable(
            route = Routes.JAVLIST,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("idolIds") { type = NavType.StringType; defaultValue = "" },
                navArgument("tagIds") { type = NavType.StringType; defaultValue = "" },
                navArgument("studioId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("seriesId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val args = entry.arguments ?: return@composable
            JavListScreen(
                title = args.getString("title").orEmpty(),
                idolIds = args.getString("idolIds").orEmpty(),
                tagIds = args.getString("tagIds").orEmpty(),
                studioId = args.getLong("studioId"),
                seriesId = args.getLong("seriesId"),
                onOpenDetail = { videoId, code -> navController.navigate(Routes.detail(videoId, code)) },
                onPlay = { videoId -> navController.navigate(Routes.player(videoId)) },
                onBack = { navController.popBackStack() },
                onSessionExpired = goLogin,
            )
        }
        composable(
            route = Routes.FAVGROUP,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val groupId = entry.arguments?.getLong("groupId") ?: return@composable
            FavGroupScreen(
                groupId = groupId,
                groupName = entry.arguments?.getString("name").orEmpty(),
                onOpenDetail = { videoId, code -> navController.navigate(Routes.detail(videoId, code)) },
                onPlay = { videoId -> navController.navigate(Routes.player(videoId)) },
                onBack = { navController.popBackStack() },
                onSessionExpired = goLogin,
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
            enterTransition = { playerFadeEnter },
            exitTransition = { playerFadeExit },
            popEnterTransition = { playerFadeEnter },
            popExitTransition = { playerFadeExit },
        ) { entry ->
            val videoId = entry.arguments?.getLong("videoId") ?: return@composable
            PlayerScreen(
                videoId = videoId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
