package com.backlogquest.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.CrashLog
import com.backlogquest.app.data.Shortcuts
import com.backlogquest.app.data.WebImport
import com.backlogquest.app.ui.BacklogApp
import com.backlogquest.app.ui.BacklogTheme

class MainActivity : ComponentActivity() {
    private val vm: BacklogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so keep the status bar icons light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // If the app ever crashes, keep the reason so it can be shown next time.
        CrashLog.install(filesDir)
        setContent {
            BacklogTheme {
                BacklogApp(vm)
            }
        }
        if (savedInstanceState == null) {
            handlePlay(intent)
            handleShare(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePlay(intent)
        handleShare(intent)
    }

    /** A page was shared from a browser: offer to add its games to the wishlist. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        intent.action = Intent.ACTION_MAIN
        val url = WebImport.firstUrl(text)
        if (url == null) vm.showToast("NO LINK FOUND\nShare a web page to add its games") else vm.webImport = url
    }

    override fun onResume() {
        super.onResume()
        // Back from a game started here: work out how long you played.
        vm.checkSession()
    }

    /** A home-screen game icon was tapped: start that game straight away. */
    private fun handlePlay(intent: Intent?) {
        val id = intent?.getStringExtra(Shortcuts.EXTRA_GAME) ?: return
        intent.removeExtra(Shortcuts.EXTRA_GAME)
        vm.play(id)
    }
}
