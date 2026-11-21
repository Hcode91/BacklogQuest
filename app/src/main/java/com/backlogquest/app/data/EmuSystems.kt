package com.backlogquest.app.data

/**
 * What each emulator plays. Used in the Emulators tab to show the systems under each emulator,
 * and to spot emulators on this phone by their app name or package.
 */
object EmuSystems {
    class Info(
        val key: String,
        /** App names it goes by (compared ignoring case, spaces and symbols). */
        val names: List<String>,
        /** Package name starts it can have. */
        val pkgs: List<String>,
        /** One line: the systems it plays. */
        val summary: String,
        /** The detail: models, boards and versions, one line each. */
        val details: List<String> = emptyList(),
        /** True when the app couldn't be confirmed and the systems are a best guess. */
        val unsure: Boolean = false,
    )

    val all: List<Info> = listOf(
        // Multi-system
        Info(
            "retroarch", listOf("RetroArch", "RetroArch Plus", "RetroArch AArch64"), listOf("com.retroarch"),
            "Many systems through downloadable cores",
            listOf(
                "A frontend: each system needs its core, from Online Updater → Core Downloader.",
                "Play Store build: up to 50 cores. RetroArch Plus (Android 8+): up to 127.",
                "Nintendo: NES, Famicom Disk System (Mesen, Nestopia, FCEUmm), SNES (Snes9x, bsnes), Game Boy / Color (Gambatte, SameBoy), GBA (mGBA, gpSP), Virtual Boy (Beetle VB), Pokémon Mini, N64 (Mupen64Plus-Next, ParaLLEl), DS (melonDS DS, DeSmuME)",
                "Sega: SG-1000, Master System, Game Gear, Mega Drive / Genesis, Mega-CD, 32X (Genesis Plus GX, PicoDrive), Saturn (Beetle Saturn, Yabause), Dreamcast / NAOMI (Flycast)",
                "Sony: PlayStation (SwanStation, Beetle PSX, PCSX ReARMed), PSP (PPSSPP)",
                "Atari: 2600 (Stella), 5200, 7800 (ProSystem), Lynx (Handy, Beetle Lynx), Jaguar (Virtual Jaguar)",
                "NEC: PC Engine / TurboGrafx-16, PC Engine CD, SuperGrafx (Beetle PCE), PC-FX",
                "SNK: Neo Geo Pocket / Color (RACE, Beetle NGP), Neo Geo AES / MVS (FinalBurn Neo)",
                "Arcade: MAME (several versions), FinalBurn Neo (CPS1, CPS2, CPS3, Neo Geo and more)",
                "Others: 3DO (Opera), ColecoVision, Intellivision (FreeIntv), Vectrex (vecx), WonderSwan / Color (Beetle Cygne), Watara Supervision, Arcadia 2001 / Interton VC 4000 (AmiArcadia)",
                "Computers: Commodore 64 / VIC-20 / C128 (VICE), Amiga (PUAE), ZX Spectrum (Fuse), Amstrad CPC (Caprice32, CrocoDS), MSX (blueMSX, fMSX), Atari ST (Hatari), DOS (DOSBox Pure), Apple II (AppleWin)",
                "Game engines: Doom (PrBoom), Quake (TyrQuake), ScummVM adventure games",
            ),
        ),
        Info(
            "eka2l1", listOf("EKA2L1"), listOf("com.github.eka2l1"),
            "Nokia N-Gage and Symbian phones (S60v1, S60v3, S60v5)",
            listOf(
                "N-Gage and N-Gage QD (2003) games, through its S60v1 support",
                "S60v1 phones, such as the Nokia 7650",
                "S60v3 phones, such as the N95 and E50, including N-Gage 2.0 games (these don't run on the original N-Gage)",
                "S60v5 touchscreen phones, such as the 5800 XpressMusic",
                "Java (J2ME) games, on S60v1 devices only (J2ME Loader is better for these)",
                "Needs the system files for each phone you emulate.",
            ),
        ),
        Info(
            "86box", listOf("86BoxDroid", "86Box"), emptyList(),
            "Old IBM-compatible PCs, 8088 to Pentium II (DOS, Windows 3.x, 95, 98)",
            listOf(
                "8088: IBM PC (1981, 1982), IBM PCjr, IBM XT (early DOS games)",
                "286: IBM AT, IBM PS/1 model 2011, IBM PS/2 model 30-286, Compaq Portable II and III, Amstrad PC5286",
                "386 and 486: late DOS and Windows 3.1 games",
                "Pentium, Pentium MMX, Pentium Pro, Pentium II and Celeron (Mendocino): Windows 95/98 games",
                "Speeds 4.77 to 733 MHz; clone CPUs from AMD, Cyrix, IDT, VIA, ST and ALi",
                "On a phone, 486 and early Pentium machines are the realistic limit.",
            ),
        ),
        Info(
            "qemu", listOf("QEMU", "QEMU-Android", "Limbo", "Limbo PC Emulator"), emptyList(),
            "Whole computers and operating systems (x86 PCs, ARM, PowerPC, SPARC)",
            listOf(
                "x86 / x86-64: DOS, Windows and Linux PCs",
                "ARM: Linux boards such as the Raspberry Pi",
                "PowerPC: PReP and Power Macintosh (old Macs)",
                "SPARC: Sun workstations",
                "Also MIPS, RISC-V and others, if your build includes them.",
            ),
        ),
        Info(
            "flycast", listOf("Flycast"), listOf("com.flycast.emulator"),
            "Sega Dreamcast, NAOMI, NAOMI 2, Atomiswave, System SP",
            listOf(
                "Dreamcast (home console)",
                "Sega NAOMI (Dreamcast-based arcade board)",
                "Sega NAOMI 2 (its more powerful successor)",
                "Sammy Atomiswave (Dreamcast-based arcade board)",
                "Sega System SP (later arcade board)",
                "BIOS optional for Dreamcast, required for NAOMI, NAOMI 2 and Atomiswave. Multiboard NAOMI games mostly don't boot.",
            ),
        ),
        Info(
            "droidarcadia", listOf("DroidArcadia"), listOf("com.amigan.droidarcadia"),
            "Emerson Arcadia 2001, Interton VC 4000, Elektor TV Games Computer",
            listOf(
                "Emerson Arcadia 2001 family (c. 1982), sold as Bandai, Grandstand, Intervision, Leisure-Vision, Leonardo, MPT-03, Palladium, Poppy, Tele-Fever and more",
                "Interton VC 4000 family (c. 1978), sold as Acetronic, Hanimex, Prinztronic, Rowtron, Voltmace, Waddingtons and more",
                "Elektor TV Games Computer (1979)",
                "The desktop version also runs Zaccaria and Malzak arcade games and Pong consoles; the Android version may not.",
            ),
        ),
        Info(
            "winuae", listOf("WinUAE", "UAE4Droid", "Uae4arm"), emptyList(),
            "Commodore Amiga computers, CD32 and CDTV",
            listOf(
                "Amiga 1000, 500, 500+, 600 (OCS/ECS: most classic games)",
                "Amiga 1200, 4000 (AGA: later games)",
                "Amiga 3000",
                "CD32 and CDTV (Amiga-based CD consoles)",
                "Needs the Kickstart ROM for each model.",
            ),
        ),
        Info(
            "applewin", listOf("AppleWinDroid", "AppleWin"), emptyList(),
            "Apple II, II+, IIe and Enhanced IIe",
            listOf(
                "Apple ][ (original) and ][+",
                "Apple //e and Enhanced //e",
                "Clones: Pravets (Bulgaria), TK3000 (Brazil), Base 64",
                "Not supported: //c, //c+, Laser 128, Apple IIgs",
            ),
        ),
        // Two systems
        Info(
            "dolphin", listOf("Dolphin", "Dolphin Emulator", "Dolphin MMJR", "MMJR2"), listOf("org.dolphinemu"),
            "Nintendo GameCube and Wii",
            listOf("GameCube", "Wii, including WiiWare and Wii Virtual Console"),
        ),
        Info(
            "melonds", listOf("melonDS"), listOf("me.magnum.melonds"),
            "Nintendo DS (and DSi, experimental)",
            listOf("Nintendo DS", "Nintendo DSi (experimental)"),
        ),
        Info(
            "deltatouch", listOf("Delta Touch"), listOf("com.opentouchgaming.deltatouch"),
            "Doom-engine PC games",
            listOf("Doom, Doom II, Final Doom", "Heretic, Hexen, Strife, Chex Quest", "Mods (WADs) for these"),
        ),
        // PC games
        Info(
            "droiddeck", listOf("DroidDeck"), listOf("com.droiddeck"),
            "Steam / Windows PC games",
            listOf("Runs Valve's ARM64 Steam client on the phone (no streaming)", "Windows games through Proton and FEX"),
        ),
        Info("gamehub", listOf("GameHub", "GameHub Lite"), emptyList(), "Windows PC games (Wine-based)"),
        Info("gamenative", listOf("GameNative", "PocketRig"), listOf("app.gamenative", "com.hcode91.pocketrig"), "Steam, Epic and GOG PC games (Wine-based)"),
        Info("winlator", listOf("Winlator"), listOf("com.winlator"), "Windows PC games (Wine-based)"),
        // One system
        Info("eden", listOf("Eden", "Eden Nightly"), listOf("dev.eden", "dev.legacy.eden"), "Nintendo Switch"),
        Info("yuzu", listOf("yuzu", "yuzu Early Access"), listOf("org.yuzu.yuzu_emu"), "Nintendo Switch"),
        Info("kenji", listOf("Kenji-NX", "KenjiNX"), listOf("org.kenjinx"), "Nintendo Switch"),
        Info("azahar", listOf("Azahar", "Lime3DS", "Citra"), listOf("org.azahar_emu", "io.github.lime3ds", "io.github.azaharplus", "org.citra"), "Nintendo 3DS"),
        Info("cemu", listOf("Cemu"), listOf("info.cemu"), "Nintendo Wii U"),
        Info("m64", listOf("M64Plus FZ", "M64Plus FZ Pro", "Mupen64Plus FZ"), listOf("org.mupen64plusae"), "Nintendo 64"),
        Info("duckstation", listOf("DuckStation"), listOf("com.github.stenzek.duckstation"), "Sony PlayStation (PS1)"),
        Info("nethersx2", listOf("NetherSX2", "AetherSX2", "ARMSX2"), listOf("xyz.aethersx2"), "Sony PlayStation 2"),
        Info("ppsspp", listOf("PPSSPP", "PPSSPP Gold"), listOf("org.ppsspp"), "Sony PSP"),
        Info("vita3k", listOf("Vita3K"), listOf("org.vita3k"), "Sony PS Vita"),
        Info(
            "rpcsx", listOf("RPCSX", "RPCSX UI", "RPCS3"), listOf("net.rpcsx"),
            "Sony PlayStation 3 (experimental)",
            listOf("Most PS3 games aren't playable on phones yet.", "Needs the PS3 firmware file (PS3UPDAT.PUP) from Sony."),
        ),
        Info(
            "armsx3", listOf("ARMSX3"), listOf("com.armsx3"),
            "Sony PlayStation 3 (experimental)",
            listOf("Based on RPCS3, from the ARMSX2 team.", "Needs the PS3 firmware file (PS3UPDAT.PUP) from Sony."),
        ),
        Info(
            "bachatas4", listOf("BachataS4", "Bachata S4", "Bachata S4+"), listOf("com.bachatas4"),
            "Sony PlayStation 4 (experimental)",
            listOf("Based on shadPS4."),
        ),
        Info("xendroid", listOf("XenDroid"), listOf("xendroid"), "Microsoft Xbox 360 (experimental)", listOf("Based on Xenia Edge.")),
        Info("j2me", listOf("J2ME Loader"), listOf("ru.playsoftware.j2meloader"), "Java (J2ME) phone games"),
        Info(
            "pocketouya", listOf("PocketOuya", "PocketMicro"), listOf("io.github.hozdogus.pocketouya", "com.hcode91.pocketmicro"),
            "Ouya games and Windows Phone games",
            listOf(
                "Ouya: converts a game's original .apk so it installs and runs on modern Android.",
                "Windows Phone: runs games through its built-in runtime.",
            ),
        ),
        // Your own emulators.
        Info(
            "pocketplug", listOf("PocketPlug"), listOf("com.hcode91.pocketplug"),
            "Plug-and-play TV games, LCD games, chess computers and vintage computers",
            listOf(
                "Plug-and-play: Retro Dance Mat, 168-in-1 and 36-in-1 Game Phone Cases, 240-in-1 Mini Arcade, A6Plus Mini Arcade, 265-in-1 and 500-in-1 handhelds, Mini Arcade Console 10-in-1, Joystick88, Atari Flashback, Air-Blaster Joystick",
                "Advance Bright TV games: 4 Player System, Pinball, Super Ping Pong, Mini Joystick 80-in-1, Joystick 14-in-1, Wireless Tennis, Kick Boxing; Millennium Arcade 3D 15-in-1",
                "Consoles: Radofin 1292 / 1392 APVS (VC 4000 family), Entex Adventure Vision, APF M-1000 / MP1000, ColecoVision carts (through the Adam)",
                "Virtual pets and tabletops: Egg Pet (Generation 1, Angel, Ocean), Chibi Pachi: Alien Fever, Coleco Alien Attack, Tomy Alien Chase, Playskool Alphie, Coleco Amaze-A-Tron, Milton Bradley Arcade Mania; LED and LCD games: Tandy Arrange Ball, Epoch Astro Command, Elektronika Ataka Asteroidov and Autoslalom, Mattel Auto Race",
                "Chess computers: Mephisto Academy, Almeria and Amsterdam, ARB V2 Sargon 4.0, Novag Accord, Fidelity Voice Chess Challenger and Advanced Voice Chess Challenger",
                "Computers: Atari 400 / 800 / XL / XE, Coleco Adam, Acorn Archimedes, A7000 and System 1, AIM 65, Altair 8800, TM 990/189, AT&T UNIX PC, NGEN CP-001",
                "Gadgets: 4004 Nixie Clock",
            ),
        ),
        Info("pocketpear", listOf("PocketPear"), listOf("com.hcode91.pocketpear"), "iPhone (iOS) games (experimental)"),
        Info(
            "pocketr5900", listOf("PocketR5900", "PS2Recomp"), listOf("com.ps2x.runner", "com.hcode91.pocketr5900"),
            "Sony PlayStation 2 (static recompiler, experimental)",
            listOf("Recompiles a PS2 game's code ahead of time instead of emulating it as it runs."),
        ),
        Info("pockettrophy", listOf("PocketTrophy"), listOf("com.hcode91.pockettrophy"), "Sony PlayStation 3", listOf("ARMSX3 with custom trophies.")),
        Info("pocketpager", listOf("PocketPager"), listOf("com.hcode91.pocketpager"), "RIM 850, 857, 950 and 957 two-way pagers"),
        Info("pocketcentury", listOf("PocketCentury"), listOf("com.hcode91.pocketcentury"), "100-in-1 Retro Gaming Console (SY-909)"),
        Info("pocketcarioca", listOf("PocketCarioca", "Zeebx"), listOf("com.hcode91.pocketcarioca", "io.github.zeebxteam.zeebx"), "Zeebo"),
        Info("pocketchroma", listOf("PocketChroma"), listOf("com.hcode91.pocketchroma"), "M&D Monon Color"),
        Info(
            "pocketpenguin", listOf("PocketPenguin"), listOf("com.hcode91.pocketpenguin"),
            "GP2X F100 / F200, GP2X Wiz, GP2X Caanoo",
            listOf("GP2X F100 and F200", "GP2X Wiz", "GP2X Caanoo"),
        ),
        Info("pocketsprout", listOf("PocketSprout"), listOf("com.hcode91.pocketsprout"), "LeapFrog Didj"),
        Info(
            "pocketreel", listOf("PocketReel"), listOf("com.hcode91.pocketreel"),
            "VideoNow discs (XP, Color / Jr / Color FX, black and white)",
            listOf("Includes the VideoNow XP interactive adventures and quiz discs."),
        ),
        Info(
            "swfpocket", listOf("SWF Pocket", "SWFlash"), listOf("dev.swfpocket"), "Flash (.swf) games and animations",
            listOf("Plays self-contained .swf files through Ruffle, offline.", "ActionScript 1/2/3 support varies by movie."),
        ),
        Info(
            "rpgpocket", listOf("RPG Pocket", "RPG Pocket Rebuilt"), listOf("com.rpgpocket.rebuilt", "com.rpgpocket"),
            "RPG Maker games (2000/2003, XP, VX, VX Ace, MV, MZ)",
            listOf(
                "RPG Maker 2000 / 2003: EasyRPG Player (WebAssembly)",
                "RPG Maker MV / MZ: in a WebView",
                "RPG Maker XP / VX / VX Ace: native mkxp-z, experimental (Android 11+)",
            ),
        ),
    )

    /** Lowercase letters and digits only, so "Bachata S4+" and "bachatas4" match. */
    fun norm(s: String): String = buildString { s.lowercase().forEach { if (it.isLetterOrDigit()) append(it) } }

    private val byName: Map<String, Info> = buildMap { all.forEach { i -> i.names.forEach { put(norm(it), i) } } }

    /** The emulator an app or saved entry is, from its package first, then its name. */
    fun find(name: String, pkg: String = ""): Info? {
        if (pkg.isNotBlank()) {
            all.firstOrNull { i -> i.pkgs.any { pkg == it || pkg.startsWith("$it.") } }
                ?.let { return it }
        }
        return byName[norm(name)]
    }

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    /** The systems line for an emulator: what you typed, or else what's known about it. */
    fun summaryFor(e: EmuEntry): String = e.systems.ifBlank { find(e.name, e.pkg)?.summary.orEmpty() }
}
