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
- Games that have no picture on the list can have one fetched from their own page straight after adding (ticked by default), the same way Fetch pictures works for CSV imports.
- You choose the platform or list name the games are filed under. Adding to a name that already exists adds to that list.

## Your own sources

Every imported list (CSV or web) has a **source**, such as "MobyGames". The import sheets suggest one from the games' links, and you can change it.

- **Discover → My sources** shows a tile for each source. Its page has the games as tiles, with search, platform and genre filters, and a Fetch pictures button. Import buttons are there too.
- **Spin → Where from** gives each source its own slice on the wheel, next to your devices, your wishlist and the Discover catalogues. **Something new → My sources** can be narrowed to one source, platform and genre.
- Imported games still appear in your wishlist under the **Imported lists** filter. The Wishlist slice and pool on the Spin tab are just your own wishlist.

## Duplicates

- **When importing**, both import sheets count games you already have (same title on the same platform in your wishlist, or any game in your games list or history) and skip them unless you untick that option.
- **Find duplicates** (in the wishlist and in Discover → My sources) lists imported games that are in your wishlist more than once, and imported games you already have. For repeats it keeps one copy (your data file's if there is one, else one with a picture) and ticks the rest for deleting. Games you already have start unticked, in case you want another platform's version. Your data file's own entries are never deleted.
- Titles are compared ignoring case and punctuation ("Pac-Man" matches "PacMan"). The same title on different platforms counts as different games.

## If the app crashes

The app keeps a note of why it crashed, and the next time it opens it says so, with a **Copy details** button. Send those details along with a bug report. (A freeze that Android closes with "isn't responding" isn't a crash inside the app, so it can't be caught this way.)

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
