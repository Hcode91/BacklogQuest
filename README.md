# Backlog Quest for Android

A native Android version of Backlog Quest: your backlog, the device-then-game picker with the two-for-two rotation rule, XP and levels, trophies and stats.

## How it gets your data

The app downloads `backlog-data.json` from this repo (the root of the `main` branch). That file holds your imported HowLongToBeat history, your wishlist, the unmarked catalog, what's on your drives, and your games list as it was in the web version.

- On a fresh install, the app copies your games and devices from the file.
- After that, your games list lives on your phone. Tap **Setup → Sync now** to refresh history, wishlist and catalog whenever the file changes.
- Updating `backlog-data.json` doesn't start a new app build.

## The tabs

- **Now**: only the games you're playing, each with the hours you've put in so far, its achievements, and Resume / Beaten it buttons.
- **Spin**: three ways to let chance decide. *What next* spins a device and then a game from your backlog (the two-for-two rule still applies). *Something new* picks a game you don't own, from your wishlist or the Discover catalogues saved on your phone. *Where from* spins a source first (your devices, your wishlist and every Discover source), then a game from it.
- **Games**: your library as cover tiles or a list.
- **Discover**: homebrew and free-game catalogues (see below).
- **Profile**: stats and the trophy room.
- **Setup**: a grid of tiles; tap one to open that part.

The status panel's achievements figure is everything you've earned: your whole Steam and RetroAchievements accounts and your PlayStation trophy total (with the details from **Setup → Achievements**), plus Xbox achievements you've ticked on games in the app. The PlayStation figure is read from your public PSNProfiles page using just your PSN ID, so it shows what that site last recorded; update your profile there after earning trophies. The accounts are re-read once a day when the app opens, or with **Recount** in Profile. Steam only answers one game at a time, so the first count can take a minute; after that only games you've played since are re-checked.

## Importing CSV lists into the wishlist

In **Games → Wishlist**, **Import CSV** adds games from CSV files such as MobyGames platform lists. You can pick several files at once.

- From each row the app takes the title, the link (a `URL` or `Link` column) and the genres (a `Genres` column, separated by commas).
- The platform comes from the file's name, so `All_Game_Boy_Games_April_2025.csv` is filed under "Game Boy". You can change it before adding. A `Platform` column, if the file has one, is used instead.
- **Fetch pictures** then visits each game's link and saves the page's main picture as its cover. It goes one game at a time with a short pause, and you can stop it and carry on later.
- Imported games show under the **Imported lists** filter, and in **Spin → Something new → My lists**, where you can narrow the spin to one platform and one genre.
- Importing a file for a platform you've already imported replaces that list. Lists can be removed from the Import CSV sheet.

## Importing from the web

**Import from web** (next to Import CSV) adds games from a web address. You can also tap Share in your browser and choose Backlog Quest ("Add to wishlist").

- **Internet Archive**: a collection, a search or a single item is read through the Archive's own API, so every item comes with its name, picture and link (and its subject tags as genres). Up to 5,000 items are read from one collection or search.
- **Other sites**: the page is offered as one game (its title and main picture). If it looks like a list, the app also offers the games linked from it, with or without pictures: it looks for the biggest run of similar links that sit side by side and go to the same sort of page, leaving out menus, paging and links to other sites (a game on its own sub-site, like `author.itch.io`, counts as the same site). If the page builds its list with scripts and a plain read finds little, it looks again in the hidden browser. That part is a best guess, so check the list and untick anything that isn't a game.
- **Lists split over pages**: when the page links to numbered pages (`/page/2/`, `?page=2`, or offsets such as `?start=40`), the sheet shows **This list has more pages**, with the address pattern it found and From / To page boxes (To defaults to the highest page linked; leave it empty to keep going until the pages run out, up to 500). Pages are read one after another with a short pause, the highest known page grows as later pages link further on, and reading stops at a page with no new games. **Stop** keeps what's been read so far.
- Games that have no picture on the list can have one fetched from their own page straight after adding (ticked by default), the same way Fetch pictures works for CSV imports.
- You choose the platform or list name the games are filed under. Adding to a name that already exists adds to that list.

## Upcoming and New

Two tabs of their own: **Upcoming** lists games coming out in the next 30 days, 3 months or 12 months, and **New** the ones released in the last 7 days, 30 days or 3 months. Only your platforms (Setup → Platforms) are included, Android and iOS too. Games are grouped by day, with their cover, your platforms they're on, genres and how many people are watching them on IGDB. A game already in your games or wishlist is marked. Tap one to open its IGDB page. **Only games people are watching** (on by default) hides the thousands of small store releases nobody follows. Lists are kept for six hours; **Refresh** asks again.

**+ WISH** puts a game on your wishlist, under the **IGDB** source (so it shows in Discover's My sources and on the Spin wheel) with its cover. A game out on several of your platforms asks which one. An upcoming game you wishlist also gets a **reminder**: the day before it comes out you get a notification ("Out tomorrow: …"), or on the day if the phone was off the day before. A background check runs twice a day and looks the dates up on IGDB again, so a delayed game is announced on its new date. Turn reminders off in **Setup → Releases**; Android 13 and later ask for permission to notify the first time.

The data comes from IGDB (the database most game-tracking apps use) through a free Twitch developer app: add its client ID and secret in **Setup → Releases**. They stay on the phone; the access token they buy is renewed by itself.

## Ports

The **Ports** tab is one list of every game decompilation, static recompilation, source port and engine remake, taken from [Awesome Decomp Games](https://submitgame.github.io/awesome-decomp-games/) (over 3,000 projects, CC0, rebuilt by its maintainers every day). The app downloads the list (about 5 MB) once a day when you open the tab, or with **Refresh**, and keeps it on the phone.

- Posts from **r/AndroidNativePorts** and **r/decomps** are added too, but only ones that link to a GitHub project. A post about a project already on the list adds a **REDDIT POST** link and an r/… badge to it; any other project gets its own entry, named after the post, with its platform and type guessed from the post's title and flair. Reddit has shut its data links to apps, so the app reads the subreddits' pages the way a browser shows them, in its invisible browser, signed in as you (**Sign in to Reddit** appears if it isn't). It reads 20 posts at a time: each refresh reads the newest posts back to the ones already saved, and **Load 20 more from Reddit** (in the On Reddit view) reads the next 20 older posts from each subreddit. A post that doesn't link to GitHub has its page opened, and counts if its text or its poster's comments link to a project. The app keeps every post it has seen. If Reddit refuses the app, **Sign in to Reddit** (shown with the message) fixes it.
- Projects found only on Reddit are looked up on GitHub, 15 per refresh (GitHub allows 60 lookups an hour): ones GitHub says don't exist are dropped, and the rest get their stars, last update and latest release. Each is looked up again after a week.
- Search, and filter by view (All, Watching, New this week, Android, Play in browser, On Reddit), type and platform; sort by best score, stars, last update, newest release or name.
- **☆ Watch** a project and it's marked **NEW RELEASE** (and listed first) when it puts out a release you haven't seen. Opening the project or its downloads counts as seeing it.
- Projects that joined the list in the last 7 days are marked **NEW** (nothing is marked the first time the list is downloaded).
- **ANDROID** marks projects that mention Android or an APK, so they probably run on the phone. **AI** marks ones the list's sources flag as mostly made with AI; you can hide them. Inactive projects (no commits for two years) are hidden unless you untick that.

## Starting games and home-screen icons

On a game, **How to start it** chooses the emulator (with the game's file or ID) or an Android app. **▶ Play** starts it and tracks play time; **Home icon** pins a tile with the game's cover to your home screen that does the same.

Emulators that are given the game, so the tile goes straight into it: RetroArch, Eden, yuzu, Kenji-NX, PPSSPP, Dolphin, DuckStation, NetherSX2, M64Plus FZ, Azahar, melonDS, Flycast, Cemu, EKA2L1, XenDroid, J2ME Loader and DroidArcadia (by file); Vita3K, ARMSX3, PocketTrophy and BachataS4 (by title ID); GameNative / PocketRig (by Steam app ID, or `GOG:id` / `EPIC:id`).

Your Pocket emulators are sent a `com.hcode91.pocket.LAUNCH_GAME` request (their `feature/launch-from-other-apps` builds answer it): PocketChroma and PocketSprout get the game file as its data; PocketPlug (set name, or `set/cart title`), PocketPenguin (library name), PocketReel (disc title), PocketCarioca (library title) and PocketOuya (name or package, Ouya or Windows Phone games) get the `game_id` extra; PocketCentury just boots. SWF Pocket (`feature/launch-and-fixes`) gets the `.swf` file as its data, and RPG Pocket gets the game's library title as `game_id`. An older build opens on its own game list. PocketR5900 opens on its own screen.

Emulators that can't be told which game to start (RPCSX, and any other emulator the app recognises on your phone, such as 86BoxDroid, WinUAE, AppleWinDroid, Delta Touch, DroidDeck or GameHub) are listed under **Other emulators on this phone**: their tile opens the emulator, you pick the game there, and play time is still tracked.

## Screenshots while you play

Turn on **Setup → Screenshots** and every game you start from Backlog Quest (the Play button or a home-screen icon) gets a screenshot every few seconds while it's on screen; you choose how many seconds (5 or more, 30 by default). They're saved to `Pictures/BacklogQuest/<game>/<game>_yyyy-MM-dd_HH-mm-ss.jpg`, named after the game rather than the emulator, so your gallery finds them. Shots pause when you leave the game, and the session ends when you come back to Backlog Quest (or after 30 minutes away). A notification shows while it's capturing, with a count.

It needs Android 11 or newer, and the **Backlog Quest screenshots** service switched on in Accessibility settings (the Setup panel has a button; sideloaded apps may first need **App info → ⋮ → Allow restricted settings**). This replaces the separate AppCapture app for games, so untick your emulators there to avoid double shots. Games that block screenshots are skipped.

## Platforms

**Setup → Platforms** lists every system you play, so none get left out. It starts with all the systems your emulators cover (Nintendo, Sony, Sega, Atari, NEC/SNK, arcade, other consoles, computers and game engines), plus Android, Legacy Android, iOS, Windows Phone, Switch 2 and Windows PC, and everything your own Pocket emulators play (PocketPlug's plug-and-play games, LCD games, chess computers and vintage computers; Zeebo, GP2X, LeapFrog Didj, Monon Color, VideoNow, RIM pagers and more). Built-in platforms added in later versions join your list automatically, unless you'd removed them. Each one shows the emulators that play it and how many of your games are on it. Remove any you don't use, add your own, or add back the built-in ones you removed. When you type a game's platform, matching platforms from this list are offered.

### Every platform, on the Now tab

Below Now playing, **Every platform** has an entry for each of your platforms. It shows the game you're playing on it (tap to open it) or "Nothing playing", so you can see at a glance which platforms are being left out. Filter to **Empty** or **Playing**, or search for one.

Games are matched to platforms by the platform set on the game, or by its device when that's blank (a game on your Switch 2 counts for Nintendo Switch 2, one on PC for Windows PC). Short and store names are understood too: PS2, PSX, Genesis, GBA, N64, Switch and so on.

**+ Add** opens a list of your backlog and wishlist games for that platform. Picking a backlog game sets it to Playing; picking a wishlist game adds it as Playing on the device you choose. **Everything** lists all your backlog and wishlist games instead, and files the one you pick under that platform.

## Your own sources

Every imported list (CSV or web) has a **source**, such as "MobyGames". The import sheets suggest one from the games' links, and you can change it.

- **Discover → My sources** shows a tile for each source. Its page has the games as tiles, with search, platform and genre filters, and a Fetch pictures button. Import buttons are there too.
- **Spin → Where from** gives each source its own slice on the wheel, next to your devices, your wishlist and the Discover catalogues. **Something new → My sources** can be narrowed to one source, platform and genre.
- Imported games still appear in your wishlist under the **Imported lists** filter. The Wishlist slice and pool on the Spin tab are just your own wishlist.

## Duplicates

- **When importing**, both import sheets count games you already have (same title on the same platform in your wishlist, or any game in your games list or history) and skip them unless you untick that option.
- **Find duplicates** (in the wishlist and in Discover → My sources) lists imported games that are in your wishlist more than once, and imported games you already have. For repeats it keeps one copy (your data file's if there is one, else one with a picture) and ticks the rest for deleting. Games you already have start unticked, in case you want another platform's version. Your data file's own entries are never deleted.
- Titles are compared ignoring case and punctuation ("Pac-Man" matches "PacMan"). The same title on different platforms counts as different games.

## Fetching for many games at once

Every tab in **Games** has a **Fetch for this list** panel. Choose how many (first 10, 25, 50, 100 or all) and it fills in the first games in the list, as currently sorted, filtered and searched, that are missing:

- **Pictures**: your games and completed games (history included) get box art from HowLongToBeat, or RAWG if you've added a key. Imported wishlist games use their own link; other wishlist games are looked up by title.
- **Achievements** (Backlog, Playing, Beaten, Dropped): from Steam, RetroAchievements, PSNProfiles or TrueAchievements, chosen by platform, as in a game's own Auto-fill.
- **Prices** (Wishlist, with an IsThereAnyDeal key).

Games are done one at a time with a short pause, each with a time limit, and the batch stops if a site stops answering three times in a row. Games tried before without a result are skipped next time, so the next press moves on; **Try them again** clears that. The wishlist can also be sorted by **Platform**.

## If the app crashes

The app keeps a note of why it crashed, and the next time it opens it says so, with a **Copy details** button. Send those details along with a bug report. (A freeze that Android closes with "isn't responding" isn't a crash inside the app, so it can't be caught this way.)

## Emulators

The **Emulators** tab keeps track of the emulators you use and tells you when there's a newer version on GitHub.

- **Add** an emulator with **+ Add emulator**, or tap one under **On this phone** (emulators the app recognises on your phone are listed there).
- **What it plays**: each emulator shows the systems it plays, on its tile and under **On this phone**. Open one to see the detail (for multi-system emulators such as RetroArch, EKA2L1, 86Box, Flycast or WinUAE, the exact models, boards and cores). For an emulator the app doesn't know, type the systems yourself; you can also replace the built-in description with your own.
- **Your version**: for an emulator on this phone the app reads the installed version itself. For one elsewhere (on your PC, say) you choose the device and type the version in.
- **GitHub link**: paste the project's address (`https://github.com/owner/project`). The app checks its releases once a day when you open the tab, or when you tap **Check for updates**. GitHub allows 60 checks an hour without signing in.
- **Up to date?** Versions are compared by their numbers (`1.19.1_GIT` matches `v1.19.1`). When the versions can't be compared (nightly builds named by date or commit, say), it says "Can't tell" instead of guessing. Tick **Include pre-releases and nightlies** for emulators that mostly publish those.
- **Updating** an emulator on this phone downloads the right APK from the release and opens Android's installer for you to confirm. The first time, Android asks you to allow Backlog Quest to install apps. If a release has several Android builds and the app can't tell which suits your phone, pick one in the emulator's sheet (and type part of its name to remember it). An update only installs over your copy if both come from the same place: a GitHub build won't replace a Play Store copy.
- For an emulator elsewhere, **Release** opens the release page so you can download it there.

## Discover

The **Discover** tab is the RetroShelf catalogue browser, merged into this app. Pick a console, pick a source, and browse homebrew and free games with search, genre filters, saved games, recently viewed and downloads.

- Sources: Universal-DB (DS/3DS), Homebrew Hub's GBA repository, GameBrew (GBA, Switch, Wii U, Wii, 3DS, DS, Game Boy/Color, PS Vita, PSP), RPGMaker.net and the Internet Archive's RPGMaker.net collection.
- A catalogue is fetched the first time you open it and then kept on your phone. Pull down to refresh it.
- Only individually reviewed files get a direct download button; everything else links to the original website. Nothing is installed or run.
- Discover keeps its own data (a small database called `discover.db`), separate from your backlog. Clearing it in **Discover → Settings** doesn't touch your games.

The tab bar scrolls sideways on phones, so swipe it to reach the tabs at either end.

## Auto-fill

Auto-fill uses RAWG, a free game database. Get a free API key at rawg.io/apidocs and paste it into **Setup → Auto-fill**.

## Building

GitHub Actions builds the APK on every push and publishes it on the Releases page.
