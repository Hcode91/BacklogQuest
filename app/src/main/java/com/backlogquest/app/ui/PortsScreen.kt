package com.backlogquest.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.PageReader
import com.backlogquest.app.data.Port
import com.backlogquest.app.data.Ports
import com.backlogquest.app.data.PortsStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The Ports tab: every decompilation, recompilation, source port and engine remake from
 * the Awesome Decomp Games list, in one place. Watch a project to be told when it has a
 * new release; projects that joined the list in the last week show as NEW.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { PortsStore.open(ctx.applicationContext) }

    val all = PortsStore.ports
    val watched = PortsStore.watched
    val firstSeen = PortsStore.firstSeen
    var view by rememberSaveable { mutableStateOf("all") }
    var type by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("score") }
    var search by rememberSaveable { mutableStateOf("") }
    var hideInactive by rememberSaveable { mutableStateOf(true) }
    var hideAi by rememberSaveable { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(PAGE) }
    var redditSignIn by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(PortsStore.redditSignedIn()) }
    if (redditSignIn) RedditSignIn {
        redditSignIn = false
        signedIn = PortsStore.redditSignedIn()
        scope.launch { PortsStore.refresh(ctx.applicationContext) }
    }

    val weekAgo = LocalDate.now().toEpochDay() - 7
    val isNew = { p: Port -> (firstSeen[p.id] ?: 0L) > weekAgo }
    val updates = remember(all, watched) { Ports.updated(all, watched) }

    // Everything but the view, type and platform choices, so their counts stay honest.
    val base = remember(all, search, hideInactive, hideAi) {
        val q = search.trim().lowercase()
        all.filter { p ->
            (!hideInactive || !p.inactive) && (!hideAi || !p.ai) &&
                (q.isEmpty() || q in p.title.lowercase() || q in p.repo.lowercase() || q in p.platform.lowercase())
        }
    }
    val views = listOf(
        "all" to "All",
        "watching" to "Watching",
        "new" to "New this week",
        "android" to "Android",
        "browser" to "Play in browser",
        "reddit" to "On Reddit",
    )
    fun inView(p: Port, v: String) = when (v) {
        "watching" -> p.id in watched
        "new" -> isNew(p)
        "android" -> p.android
        "browser" -> p.browser
        "reddit" -> p.redditUrl.isNotBlank()
        else -> true
    }
    val viewCounts = remember(base, watched, firstSeen) { views.associate { (k, _) -> k to base.count { inView(it, k) } } }
    val inThisView = remember(base, view, watched, firstSeen) { base.filter { inView(it, view) } }
    val typeCounts = remember(inThisView) { inThisView.groupingBy { it.type }.eachCount() }
    val platforms = remember(inThisView, type) {
        inThisView.filter { type.isBlank() || it.type == type }
            .groupingBy { it.platform }.eachCount().entries.sortedByDescending { it.value }
    }
    val shown = remember(inThisView, type, platform, sort, updates) {
        val list = inThisView.filter { (type.isBlank() || it.type == type) && (platform.isBlank() || it.platform == platform) }
        val sorted = when (sort) {
            "stars" -> list.sortedByDescending { it.stars }
            "updated" -> list.sortedByDescending { it.lastCommit }
            "release" -> list.sortedByDescending { it.releaseDate }
            // Reddit projects have no score: rank them by stars among themselves.
            "score" -> list.sortedWith(compareByDescending<Port> { it.score }.thenByDescending { it.stars })
            "az" -> list.sortedBy { it.title.lowercase() }
            else -> list.sortedByDescending { it.score }
        }
        // Watched projects with a new release always come first.
        sorted.sortedByDescending { it.id in updates }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("PORTS", Modifier.weight(1f))
                    if (all.isNotEmpty()) Hint("${shown.size} of ${all.size}")
                }
                Hint(
                    "Decomps, recomps, source ports and engine remakes, from the Awesome Decomp Games list" +
                        (if (PortsStore.listDate.isNotBlank()) " (updated ${PortsStore.listDate})" else "") +
                        " and posts on r/AndroidNativePorts and r/decomps that link to a GitHub project." +
                        " Tap a project to open it. Watch one to be told here when it has a new release.",
                )
                if (updates.isNotEmpty()) {
                    Text(
                        "★ ${updates.size} watched project${if (updates.size == 1) " has" else "s have"} a new release",
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable { view = "watching" }.padding(vertical = 4.dp),
                        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it; limit = PAGE },
                    placeholder = { Text("Search games, repos or platforms") },
                    singleLine = true,
                    colors = bqFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipRow(views.map { (k, l) -> k to "$l ${viewCounts[k] ?: 0}" }, view) { view = it; limit = PAGE }
                ChipRow(
                    listOf("" to "All types") + Ports.TYPES.filterKeys { (typeCounts[it] ?: 0) > 0 }
                        .map { (k, l) -> k to "$l ${typeCounts[k]}" },
                    type,
                ) { type = it; platform = ""; limit = PAGE }
                if (platforms.size > 1) {
                    ChipRow(listOf("" to "All platforms") + platforms.map { it.key to "${it.key} ${it.value}" }, platform) {
                        platform = it; limit = PAGE
                    }
                }
                ChipRow(
                    listOf("score" to "Best", "stars" to "Most stars", "updated" to "Recently updated", "release" to "Newest release", "az" to "A–Z"),
                    sort,
                ) { sort = it }
                CheckRow("Hide inactive projects", "No commits for two years.", hideInactive) { hideInactive = !hideInactive; limit = PAGE }
                CheckRow("Hide AI-made projects", "Ones the list's sources flag as mostly made with AI.", hideAi) { hideAi = !hideAi; limit = PAGE }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(if (PortsStore.busy) "LOADING…" else "REFRESH", enabled = !PortsStore.busy) {
                        scope.launch { PortsStore.refresh(ctx.applicationContext) }
                    }
                    if (PortsStore.busy && all.isNotEmpty()) Hint("Updating…")
                }
                if (view == "reddit") RedditMore(scope, ctx)
            }
        }

        PortsStore.error?.let { e -> item { Panel { Text(e, color = BQ.Ink, fontSize = 14.sp) } } }
        PortsStore.redditNote?.let { n ->
            item {
                Panel {
                    Text(n, color = BQ.Ink, fontSize = 14.sp)
                    Hint(
                        if (signedIn) "You're signed in to Reddit in the app. Try Refresh again later; Reddit sometimes blocks for a while."
                        else "Reddit only shows its pages to the app when you're signed in. Sign in to Reddit inside the app, then refresh.",
                    )
                    PixelButton(if (signedIn) "SIGN IN AGAIN" else "SIGN IN TO REDDIT", kind = BtnKind.B) { redditSignIn = true }
                }
            }
        }
        if (all.isEmpty() && PortsStore.busy) item { Hint("Downloading the list (about 5 MB, once a day)…") }
        if (all.isNotEmpty() && shown.isEmpty()) item {
            Hint(if (view == "watching") "You're not watching anything here yet. Tap ☆ WATCH on a project." else "Nothing matches.")
        }

        items(shown.take(limit), key = { it.id }) { p ->
            PortRow(
                p,
                watching = p.id in watched,
                hasUpdate = p.id in updates,
                isNew = isNew(p),
                onOpen = {
                    PortsStore.markSeen(ctx.applicationContext, p)
                    open(ctx, p.url)
                },
                onRelease = {
                    PortsStore.markSeen(ctx.applicationContext, p)
                    open(ctx, p.releaseUrl)
                },
                onPlay = { open(ctx, p.playUrl) },
                onReddit = { open(ctx, p.redditUrl) },
                onWatch = { PortsStore.toggleWatch(ctx.applicationContext, p) },
            )
        }
        if (shown.size > limit) item {
            PixelButton("SHOW MORE (${shown.size - limit} LEFT)", Modifier.fillMaxWidth()) { limit += PAGE }
        }
        if (view == "reddit" && shown.size <= limit && all.isNotEmpty()) item {
            RedditMore(scope, ctx)
        }
        if (all.isNotEmpty()) item {
            Text(
                "List: Awesome Decomp Games (CC0) · open the website",
                Modifier.clickable { open(ctx, Ports.SITE) }.padding(vertical = 8.dp),
                color = BQ.Muted, fontSize = 12.sp,
            )
        }
    }
}

private const val PAGE = 150

/** Reads the next 20 older posts from each subreddit; posts already read are never asked for again. */
@Composable
private fun RedditMore(scope: kotlinx.coroutines.CoroutineScope, ctx: android.content.Context) {
    when {
        PortsStore.redditEnded -> Hint("That's every post Reddit lists for both subreddits.")
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PixelButton(
                if (PortsStore.redditBusy) "READING REDDIT…" else "LOAD 20 MORE FROM REDDIT",
                Modifier.fillMaxWidth(),
                enabled = !PortsStore.redditBusy && !PortsStore.busy,
            ) { scope.launch { PortsStore.loadMoreReddit(ctx.applicationContext) } }
            Hint("Reads the next 20 older posts from each subreddit. Posts already read are kept, not read again.")
        }
    }
}
/** Reddit's older sign-in page: a plain form that works in any browser. */
private const val OLD_LOGIN = "https://old.reddit.com/login"
private const val NEW_LOGIN = "https://www.reddit.com/login/"

private fun open(ctx: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "today", "3 days ago", "2 months ago" for a "2026-11-23" date. */
private fun ago(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
    val days = ChronoUnit.DAYS.between(d, LocalDate.now())
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 31 -> "$days days ago"
        days < 365 -> "${days / 30} month${if (days / 30 == 1L) "" else "s"} ago"
        else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
    }
}

private fun stars(n: Int): String = when {
    n >= 10_000 -> "${n / 1000}k"
    n >= 1000 -> "%.1fk".format(n / 1000.0)
    else -> "$n"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortRow(
    p: Port,
    watching: Boolean,
    hasUpdate: Boolean,
    isNew: Boolean,
    onOpen: () -> Unit,
    onRelease: () -> Unit,
    onPlay: () -> Unit,
    onReddit: () -> Unit,
    onWatch: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth(), onClick = onOpen, padding = 10.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${p.platform} · ${Ports.typeLabel(p.type)}", color = BQ.B, fontSize = 12.sp, maxLines = 1)
            }
            Text(
                if (watching) "★ WATCHING" else "☆ WATCH",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onWatch).padding(horizontal = 6.dp, vertical = 6.dp),
                color = if (watching) BQ.B else BQ.Muted, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            )
        }
        val about = p.note.ifBlank { p.description }
        if (about.isNotBlank()) Text(about, color = BQ.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hasUpdate) StatusLabel("NEW RELEASE", BQ.B, BQ.Shell)
            if (isNew) StatusLabel("NEW", BQ.OkBg, BQ.Ok)
            if (p.android) StatusLabel("ANDROID", BQ.OkBg, BQ.Ok)
            if (p.browser) StatusLabel("BROWSER")
            if (p.redditSub.isNotBlank()) StatusLabel("r/${p.redditSub.uppercase()}", BQ.OwnBg, BQ.B)
            if (p.ai) StatusLabel("AI", BQ.CoolBg, BQ.CoolFg)
            if (p.inactive || p.archived) StatusLabel(if (p.archived) "ARCHIVED" else "INACTIVE")
        }
        val meta = listOfNotNull(
            if (p.fromReddit && p.lastCommit.isBlank()) "checking GitHub…" else "★ ${stars(p.stars)}",
            p.lastCommit.takeIf { it.isNotBlank() }?.let { "updated ${ago(it)}" },
            p.release.takeIf { it.isNotBlank() }?.let { r -> r + (p.releaseDate.takeIf { it.isNotBlank() }?.let { " (${ago(it)})" } ?: "") },
        ).joinToString(" · ")
        Text(meta, color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.releaseUrl.isNotBlank() || p.playUrl.isNotBlank() || p.redditUrl.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (p.releaseUrl.isNotBlank()) ActionText("DOWNLOADS ›", onRelease)
                if (p.playUrl.isNotBlank()) ActionText("PLAY IN BROWSER ›", onPlay)
                if (p.redditUrl.isNotBlank()) ActionText("REDDIT POST ›", onReddit)
            }
        }
    }
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
    )
}

/**
 * Reddit's sign-in page in a full-screen browser. The app's invisible browser shares its
 * cookies, so once you're signed in here the Ports tab reads Reddit as you.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RedditSignIn(onDone: () -> Unit) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(BQ.Shell).systemBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("SIGN IN TO REDDIT", Modifier.weight(1f))
                PixelButton("DONE", kind = BtnKind.B, onClick = onDone)
            }
            Hint(
                "Sign in with your Reddit username and password (\"Continue with Google/Apple\" doesn't work inside apps), " +
                    "then tap DONE. The app only uses it to read r/AndroidNativePorts and r/decomps.",
            )
            var web by remember { mutableStateOf<WebView?>(null) }
            var oldPage by remember { mutableStateOf(true) }
            Text(
                if (oldPage) "Page blank or not working? Try Reddit's newer sign-in page ›" else "Back to the simple sign-in page ›",
                Modifier.clickable {
                    oldPage = !oldPage
                    web?.loadUrl(if (oldPage) OLD_LOGIN else NEW_LOGIN)
                }.padding(vertical = 4.dp),
                color = BQ.B, fontSize = 13.sp,
            )
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        // White behind the page, so a page that's still loading doesn't look like a black screen.
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // Reddit refuses some built-in browsers: present as the phone's Chrome.
                        PageReader.chromeAgent(c).takeIf { it.isNotBlank() }?.let { settings.userAgentString = it }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                CookieManager.getInstance().flush()
                            }
                        }
                        loadUrl(OLD_LOGIN)
                        web = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}
