package com.tachiup.data

/** Extension stores targeted by TachiUp. */
object Repos {
    /**
     * Keiyoushi is the only maintained store now: Yuzono's repos were deprecated and point at it.
     * The legacy index URL is kept on purpose — it's the one every Mihon/Komikku version accepts,
     * and newer versions follow it through repo.json to the protobuf index, same as TachiUp.
     */
    val KEIYOUSHI = ExtensionRepo(
        key = "keiyoushi",
        name = "Keiyoushi",
        storeUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
        discordUrl = "https://discord.gg/3FbCpdKbdY",
        githubRepo = "keiyoushi/extensions-source",
    )

    val ALL = listOf(KEIYOUSHI)
}
