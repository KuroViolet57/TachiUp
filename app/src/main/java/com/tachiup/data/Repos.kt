package com.tachiup.data

/** Built-in repositories targeted by TachiUp. */
object Repos {
    val KEIYOUSHI = ExtensionRepo(
        key = "keiyoushi",
        name = "Keiyoushi",
        baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
        discordUrl = "https://discord.gg/3FbCpdKbdY",
        githubRepo = "keiyoushi/extensions",
    )

    val YUZONO = ExtensionRepo(
        key = "yuzono",
        name = "Yuzono",
        baseUrl = "https://raw.githubusercontent.com/yuzono/manga-repo/repo",
        discordUrl = "https://discord.gg/yuzono",
        githubRepo = "yuzono/manga-repo",
    )

    val YUZONO_CURSED = ExtensionRepo(
        key = "yuzono-cursed",
        name = "Yuzono Cursed",
        baseUrl = "https://raw.githubusercontent.com/yuzono/cursed-manga-repo/repo",
        discordUrl = "https://discord.gg/yuzono",
        githubRepo = "yuzono/cursed-manga-repo",
    )

    val ALL = listOf(KEIYOUSHI, YUZONO, YUZONO_CURSED)

    fun byKey(key: String): ExtensionRepo? = ALL.firstOrNull { it.key == key }
}
