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

    val ALL = listOf(KEIYOUSHI, YUZONO)

    fun byKey(key: String): ExtensionRepo? = ALL.firstOrNull { it.key == key }
}
