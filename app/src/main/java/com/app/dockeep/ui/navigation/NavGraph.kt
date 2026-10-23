package com.app.dockeep.ui.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.app.dockeep.ui.screens.files.FilesScreen
import com.app.dockeep.ui.screens.onboard.OnboardingScreen
import com.app.dockeep.ui.screens.settings.SettingsScreen
import com.app.dockeep.utils.Constants.FILES_ROUTE
import com.app.dockeep.utils.Constants.FILES_DIR
import com.app.dockeep.utils.Constants.PATH_ARG
import com.app.dockeep.utils.Constants.URI_ARG
import com.app.dockeep.utils.Constants.GUIDE_ROUTE
import com.app.dockeep.utils.Constants.SETTINGS_ROUTE

/** One vault, two independent navigation stacks. The right host stays outside the left routes. */
@Composable
fun NavGraph(navController: NavHostController,firstStart: Boolean) {
    val context=LocalContext.current
    var rightExpanded by rememberSaveable { mutableStateOf(false) }
    var activeRight by rememberSaveable { mutableStateOf(false) }
    val rightController=rememberNavController()
    val leftEntry by navController.currentBackStackEntryAsState()
    val rightEntry by rightController.currentBackStackEntryAsState()
    val route="$FILES_ROUTE/{$PATH_ARG}/{$URI_ARG}"
    val showRight=rightExpanded && leftEntry?.destination?.route==route
    val startRoute=if(firstStart) GUIDE_ROUTE else "${FILES_ROUTE}/${FILES_DIR}/"
    BackHandler(showRight && if(activeRight) (rightEntry?.let { rightController.previousBackStackEntry==null } ?: true) else navController.previousBackStackEntry==null) {
        rightExpanded=false;activeRight=false
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight().testTag("left-pane").pointerInput(Unit) {
            awaitPointerEventScope { while(true) { val e=awaitPointerEvent(PointerEventPass.Initial);if(e.changes.any {it.pressed}) activeRight=false } }
        }) {
            NavHost(navController,startDestination=startRoute) {
                composable(route) { entry ->
                    val path=FolderRoute.decode(entry.arguments?.getString(PATH_ARG).orEmpty())
                    val uri=FolderRoute.decodeUri(entry.arguments?.getString(URI_ARG).orEmpty())
                    FilesScreen(path,uri,navController,
                        onNavigate={ target,name ->
                            try { navController.navigate(FolderRoute.create(target,name)) }
                            catch(e: Exception) { android.widget.Toast.makeText(context,"无法打开文件夹：${e.message}",android.widget.Toast.LENGTH_LONG).show() }
                        },
                        paneKey="left",compact=showRight,rightExpanded=showRight,
                        onToggleRight={rightExpanded=!rightExpanded;if(!rightExpanded) activeRight=false},
                        handleSystemBack=!showRight || !activeRight)
                }
                composable(SETTINGS_ROUTE) { SettingsScreen(onGoBack={navController.popBackStack()}) }
                composable(GUIDE_ROUTE) {
                    OnboardingScreen(onDone={ navController.navigate("${FILES_ROUTE}/${FILES_DIR}/") { popUpTo(GUIDE_ROUTE){inclusive=true} } })
                }
            }
        }
        if(showRight) {
            VerticalDivider(Modifier.fillMaxHeight(),thickness=1.dp)
            Box(Modifier.weight(1f).fillMaxHeight().testTag("right-pane").pointerInput(Unit) {
                awaitPointerEventScope { while(true) { val e=awaitPointerEvent(PointerEventPass.Initial);if(e.changes.any {it.pressed}) activeRight=true } }
            }) {
                NavHost(rightController,startDestination="${FILES_ROUTE}/${FILES_DIR}/") {
                    composable(route) { entry ->
                        val path=FolderRoute.decode(entry.arguments?.getString(PATH_ARG).orEmpty())
                        val uri=FolderRoute.decodeUri(entry.arguments?.getString(URI_ARG).orEmpty())
                        FilesScreen(path,uri,rightController,
                            onNavigate={target,name->rightController.navigate(FolderRoute.create(target,name))},
                            paneKey="right",compact=true,handleSystemBack=activeRight,
                            onSettings={navController.navigate(SETTINGS_ROUTE)})
                    }
                }
            }
        }
    }
}
