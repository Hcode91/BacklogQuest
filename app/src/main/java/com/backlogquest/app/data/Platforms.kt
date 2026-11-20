package com.backlogquest.app.data

/**
 * Every platform you can play: the systems your emulators cover, plus devices and
 * platforms you play natively. It's the starting list for "My platforms" in Setup,
 * which you can trim or add to.
 */
object Platforms {
    class Platform(val name: String, val family: String, val playedWith: List<String> = emptyList())

    private fun group(family: String, vararg items: Pair<String, List<String>>) =
        items.map { (name, with) -> Platform(name, family, with) }

    private val RA = "RetroArch"

    val defaults: List<Platform> = listOf(
        group(
            "Nintendo",
            "NES" to listOf(RA),
            "Famicom Disk System" to listOf(RA),
            "SNES" to listOf(RA),
            "Nintendo 64" to listOf("M64Plus FZ", RA),
            "Game Boy" to listOf(RA),
            "Game Boy Color" to listOf(RA),
            "Game Boy Advance" to listOf(RA),
            "Virtual Boy" to listOf(RA),
            "Pokémon Mini" to listOf(RA),
            "Nintendo DS" to listOf("melonDS", RA),
            "Nintendo DSi" to listOf("melonDS"),
            "Nintendo 3DS" to listOf("Azahar"),
            "GameCube" to listOf("Dolphin"),
            "Wii" to listOf("Dolphin"),
            "Wii U" to listOf("Cemu"),
            "Nintendo Switch" to listOf("Eden", "yuzu", "Kenji-NX"),
            "Nintendo Switch 2" to listOf("Your Switch 2"),
        ),
        group(
            "Sony",
            "PlayStation" to listOf("DuckStation", RA),
            "PlayStation 2" to listOf("NetherSX2"),
            "PSP" to listOf("PPSSPP", RA),
            "PS Vita" to listOf("Vita3K"),
            "PlayStation 3" to listOf("RPCSX", "ARMSX3"),
            "PlayStation 4" to listOf("BachataS4"),
        ),
        group(
            "Microsoft",
            "Xbox 360" to listOf("XenDroid"),
        ),
        group(
            "Sega",
            "SG-1000" to listOf(RA),
            "Master System" to listOf(RA),
            "Game Gear" to listOf(RA),
            "Mega Drive / Genesis" to listOf(RA),
            "Mega-CD" to listOf(RA),
            "32X" to listOf(RA),
            "Saturn" to listOf(RA),
            "Dreamcast" to listOf("Flycast", RA),
            "Sega NAOMI" to listOf("Flycast"),
            "Sega NAOMI 2" to listOf("Flycast"),
            "Sega System SP" to listOf("Flycast"),
        ),
        group(
            "Atari",
            "Atari 2600" to listOf(RA),
            "Atari 5200" to listOf(RA),
            "Atari 7800" to listOf(RA),
            "Atari Lynx" to listOf(RA),
            "Atari Jaguar" to listOf(RA),
        ),
        group(
            "NEC and SNK",
            "PC Engine / TurboGrafx-16" to listOf(RA),
            "PC Engine CD" to listOf(RA),
            "SuperGrafx" to listOf(RA),
            "PC-FX" to listOf(RA),
            "Neo Geo" to listOf(RA),
            "Neo Geo Pocket" to listOf(RA),
            "Neo Geo Pocket Color" to listOf(RA),
        ),
        group(
            "Arcade",
            "Arcade (MAME / FinalBurn Neo)" to listOf(RA),
            "Atomiswave" to listOf("Flycast"),
        ),
        group(
            "Other consoles",
            "3DO" to listOf(RA),
            "ColecoVision" to listOf(RA),
            "Intellivision" to listOf(RA),
            "Vectrex" to listOf(RA),
            "WonderSwan" to listOf(RA),
            "WonderSwan Color" to listOf(RA),
            "Watara Supervision" to listOf(RA),
            "Emerson Arcadia 2001" to listOf("DroidArcadia", RA),
            "Interton VC 4000" to listOf("DroidArcadia", RA),
            "Elektor TV Games Computer" to listOf("DroidArcadia"),
            "Ouya" to listOf("PocketOuya"),
        ),
        group(
            "Phones",
            "Android" to listOf("Your phone"),
            "Legacy Android" to emptyList(),
            "iOS" to emptyList(),
            "Windows Phone" to emptyList(),
            "Nokia N-Gage" to listOf("EKA2L1"),
            "Symbian phones" to listOf("EKA2L1"),
            "Java phone games" to listOf("J2ME Loader", "EKA2L1"),
        ),
        group(
            "Computers",
            "Windows PC" to listOf("Your PC", "GameHub", "GameNative", "DroidDeck"),
            "Windows 9x PC" to listOf("86BoxDroid"),
            "DOS PC" to listOf("86BoxDroid", RA),
            "Commodore Amiga" to listOf("WinUAE", RA),
            "Amiga CD32" to listOf("WinUAE"),
            "Commodore CDTV" to listOf("WinUAE"),
            "Commodore 64" to listOf(RA),
            "Commodore VIC-20" to listOf(RA),
            "Commodore 128" to listOf(RA),
            "ZX Spectrum" to listOf(RA),
            "Amstrad CPC" to listOf(RA),
            "MSX" to listOf(RA),
            "Atari ST" to listOf(RA),
            "Apple II" to listOf("AppleWinDroid", RA),
        ),
        group(
            "Game engines and players",
            "Doom engine" to listOf("Delta Touch", RA),
            "Quake" to listOf(RA),
            "ScummVM" to listOf(RA),
            "Flash games" to listOf("SWF Pocket"),
            "RPG Maker" to listOf("RPG Pocket"),
        ),
    ).flatten()

    val defaultNames: List<String> = defaults.map { it.name }

    private val byName = defaults.associateBy { it.name.lowercase() }

    fun info(name: String): Platform? = byName[name.trim().lowercase()]

    /** The group a platform belongs to; your own additions go under "Your own". */
    fun familyOf(name: String): String = info(name)?.family ?: "Your own"

    /** Families in the order of the built-in list, then "Your own". */
    val families: List<String> = defaults.map { it.family }.distinct() + "Your own"
}
