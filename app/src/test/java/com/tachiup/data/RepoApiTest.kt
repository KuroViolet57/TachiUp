package com.tachiup.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalSerializationApi::class)
class RepoApiTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val routes = mutableMapOf<String, MockResponse>()
    private val api = RepoApi()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                routes[request.path] ?: MockResponse().setResponseCode(404)
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String) = server.url(path).toString()

    private fun repo(storePath: String) = ExtensionRepo(
        key = "test",
        name = "Test",
        storeUrl = url(storePath),
        discordUrl = "",
        githubRepo = "",
    )

    /** Keiyoushi's layout today: the legacy index is a stub, repo.json points at a gzipped protobuf index. */
    @Test
    fun resolvesLegacyUrlToProtobufIndex() = runBlocking {
        routes["/repo/index.min.json"] = MockResponse().setBody(OUTDATED_STUB)
        routes["/repo/repo.json"] = MockResponse().setBody(
            """{"index_v2":"${url("/raw/repo/index.pb")}","meta":{"name":"Keiyoushi","website":"https://keiyoushi.github.io","signingKeyFingerprint":"$KEIYOUSHI_KEY"}}""",
        )
        routes["/raw/repo/index.pb"] = MockResponse().setBody(Buffer().write(fixture()))

        val store = api.fetchStore(repo("/repo/index.min.json"))

        assertEquals("Keiyoushi", store.name)
        assertEquals("KEI", store.badgeLabel)
        assertEquals(KEIYOUSHI_KEY, store.signingKey)
        assertEquals("https://keiyoushi.github.io", store.website)
        assertEquals("https://discord.gg/3FbCpdKbdY", store.discord)
        assertEquals(url("/raw/repo/index.pb"), store.indexUrl)
        assertEquals(
            listOf(
                "eu.kanade.tachiyomi.extension.all.cubari",
                "eu.kanade.tachiyomi.extension.all.deviantart",
                "eu.kanade.tachiyomi.extension.en.omegascans",
                "eu.kanade.tachiyomi.extension.en.akaicomic",
            ),
            store.extensions.map { it.pkg },
        )

        val cubari = store.extensions[0]
        assertEquals("Cubari", cubari.name)
        assertEquals(106000L, cubari.versionCode)
        assertEquals("1.6.0", cubari.versionName)
        assertEquals("1.6", cubari.libVersion)
        assertEquals("all", cubari.lang) // sources span several languages
        assertEquals(3, cubari.sources.size)
        assertEquals(ContentWarning.SAFE, cubari.contentWarning)
        assertEquals(
            "https://github.com/keiyoushi/extensions/releases/download/24bc6c2/tachiyomi-all.cubari-v1.6.0.apk",
            cubari.apkUrl,
        )
        assertEquals("tachiyomi-all.cubari-v1.6.0.apk", cubari.apkFileName)
        assertTrue(cubari.iconUrl.endsWith("/ic_launcher.png"))

        assertEquals(ContentWarning.MIXED, store.extensions[1].contentWarning)
        assertEquals(ContentWarning.NSFW, store.extensions[2].contentWarning)
        assertEquals("en", store.extensions[2].lang)
        assertEquals("1.4", store.extensions[3].libVersion)
        assertEquals(104003L, store.extensions[3].versionCode)

        assertEquals(
            listOf("/repo/index.min.json", "/repo/repo.json", "/raw/repo/index.pb"),
            List(server.requestCount) { server.takeRequest().path },
        )
    }

    @Test
    fun readsLegacyRepoWithoutIndexV2() = runBlocking {
        routes["/old/repo.json"] = MockResponse().setBody(
            """{"meta":{"name":"Old","shortName":"OLD","website":"https://old.example","signingKeyFingerprint":"ABCDEF"}}""",
        )
        routes["/old/index.min.json"] = MockResponse().setBody(
            """[{"name":"Tachiyomi: Foo","pkg":"eu.kanade.tachiyomi.extension.en.foo","apk":"tachiyomi-en.foo-v1.4.7.apk",
               |"lang":"en","code":7,"version":"1.4.7","nsfw":1,
               |"sources":[{"name":"Foo","lang":"en","id":"123","baseUrl":"https://foo.example"}]}]
            """.trimMargin(),
        )

        val store = api.fetchStore(repo("/old/repo.json"))

        assertEquals("OLD", store.badgeLabel)
        assertEquals("abcdef", store.signingKey)
        assertEquals(url("/old/index.min.json"), store.indexUrl)
        val foo = store.extensions.single()
        assertEquals("Foo", foo.name)
        assertEquals(url("/old/apk/tachiyomi-en.foo-v1.4.7.apk"), foo.apkUrl)
        assertEquals(7L, foo.versionCode)
        assertEquals("1.4", foo.libVersion)
        assertEquals(ContentWarning.NSFW, foo.contentWarning)
        assertEquals(123L, foo.sources.single().id)
    }

    @Test
    fun followsExtensionListUrl() = runBlocking {
        val list = ProtoExtensionList(
            listOf(
                ProtoExtension(
                    name = "Bar",
                    packageName = "eu.kanade.tachiyomi.extension.ja.bar",
                    resources = ProtoResources(apkUrl = "https://cdn.example/bar.apk"),
                    extensionLib = "1.6",
                    versionCode = 106002,
                    versionName = "1.6.2",
                    contentWarning = 1,
                    sources = listOf(ProtoSource(id = 1, name = "Bar", language = "ja")),
                ),
            ),
        )
        val store = ProtoStore(
            name = "Split",
            badgeLabel = "SPL",
            signingKey = "FF00",
            extensionListUrl = url("/split/list.pb"),
        )
        routes["/split/index.pb"] = MockResponse().setBody(Buffer().write(ProtoBuf.encodeToByteArray(store)))
        routes["/split/list.pb"] = MockResponse().setBody(Buffer().write(ProtoBuf.encodeToByteArray(list)))

        val result = api.fetchStore(repo("/split/index.pb"))

        assertEquals("ff00", result.signingKey)
        assertEquals("ja", result.extensions.single().lang)
        assertEquals(106002L, result.extensions.single().versionCode)
    }

    @Test
    fun downloadsApkFollowingRedirects() = runBlocking {
        val apkBytes = ByteArray(4096) { it.toByte() }
        routes["/releases/download/x/foo.apk"] = MockResponse()
            .setResponseCode(302)
            .setHeader("Location", url("/assets/foo.apk"))
        routes["/assets/foo.apk"] = MockResponse().setBody(Buffer().write(apkBytes))
        val ext = sampleExtension(apkUrl = url("/releases/download/x/foo.apk"))

        val file = api.downloadApk(ext, tmp.root)

        assertEquals("${ext.pkg}-${ext.versionCode}.apk", file.name)
        assertTrue(apkBytes.contentEquals(file.readBytes()))
        assertEquals(listOf(file.name), tmp.root.list()!!.toList())
    }

    @Test
    fun updateAndTrustStateFollowVersionCodeAndSigningKey() {
        val store = ExtensionStore(
            repo = Repos.KEIYOUSHI, name = "Keiyoushi", badgeLabel = "KEI", signingKey = KEIYOUSHI_KEY,
            website = "", discord = null, indexUrl = "", extensions = emptyList(),
        )
        val ext = sampleExtension(versionCode = 106004)
        fun status(code: Long, signatures: List<String>) = ExtensionStatus(
            installed = InstalledExtension(ext.pkg, "Foo", "1.4.4", code, signatures),
            repoExtension = ext,
            store = store,
        )

        // Old-scheme build (versionCode 4) of the same extension: an update even though names look alike.
        assertEquals(UpdateState.UPDATE_AVAILABLE, status(4, listOf(KEIYOUSHI_KEY)).state)
        assertEquals(UpdateState.UP_TO_DATE, status(106004, listOf(KEIYOUSHI_KEY)).state)
        assertEquals(UpdateState.FOREIGN_SIGNATURE, status(106004, listOf(YUZONO_KEY)).state)
        // Rotated keys keep the old certificate in their history, which Mihon accepts too.
        assertEquals(UpdateState.UP_TO_DATE, status(106004, listOf(YUZONO_KEY, KEIYOUSHI_KEY)).state)
        // Unreadable signatures aren't reported as foreign.
        assertFalse(status(106004, emptyList()).foreignSigner)
        assertEquals(
            UpdateState.NOT_IN_REPO,
            ExtensionStatus(status(1, emptyList()).installed, repoExtension = null, store = null).state,
        )
    }

    private fun fixture(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("keiyoushi-index-sample.pb")!!.use { it.readBytes() }

    private fun sampleExtension(
        apkUrl: String = "https://example/foo.apk",
        versionCode: Long = 106004,
    ) = RepoExtension(
        name = "Foo",
        pkg = "eu.kanade.tachiyomi.extension.en.foo",
        apkUrl = apkUrl,
        iconUrl = "",
        libVersion = "1.6",
        versionCode = versionCode,
        versionName = "1.6.4",
        lang = "en",
        contentWarning = ContentWarning.SAFE,
        sources = emptyList(),
    )

    private companion object {
        const val KEIYOUSHI_KEY = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
        const val YUZONO_KEY = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905"

        /** What keiyoushi/extensions serves at repo/index.min.json now. */
        const val OUTDATED_STUB = """[{"name":"Outdated App","pkg":"eu.kanade.tachiyomi.extension.all.keiyoushi",
            "apk":"tachiyomi-all.keiyoushi-v1.4.1.apk","lang":"all","code":1,"version":"1.4.1","nsfw":0,
            "sources":[{"name":"Outdated App","lang":"all","id":"1","baseUrl":"https://keiyoushi.github.io"}]}]"""
    }
}
