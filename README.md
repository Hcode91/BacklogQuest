# Backlog Quest for Android

A native Android version of Backlog Quest: your backlog, the device-then-game picker with the two-for-two rotation rule, XP and levels, trophies and stats.

## How it gets your data

The app downloads `backlog-data.json` from this repo (the root of the `main` branch). That file holds your imported HowLongToBeat history, your wishlist, the unmarked catalog, what's on your drives, and your games list as it was in the web version.

- On a fresh install, the app copies your games and devices from the file.
- After that, your games list lives on your phone. Tap **Setup → Sync now** to refresh history, wishlist and catalog whenever the file changes.
- Updating `backlog-data.json` doesn't start a new app build.

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
