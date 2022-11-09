package be.zvz.dcnewarticle

import be.zvz.dcnewarticle.setting.Setting
import be.zvz.kotlininside.KotlinInside
import be.zvz.kotlininside.api.article.ArticleList
import be.zvz.kotlininside.api.article.ArticleRead
import be.zvz.kotlininside.json.JsonBrowser
import be.zvz.kotlininside.session.user.Anonymous
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookEmbed
import club.minnced.discord.webhook.send.WebhookEmbedBuilder
import com.coreoz.wisp.Scheduler
import com.coreoz.wisp.schedule.cron.CronExpressionSchedule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.blackbird.BlackbirdModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

class App {
    private val logger = LoggerFactory.getLogger(App::class.java)
    private val jackson = JsonMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerKotlinModule()
        .registerModule(BlackbirdModule())
    private val setting: Setting by lazy {
        logger.info("설정 로드 중!")
        val s: Setting = BufferedInputStream(FileInputStream("setting.json")).use(jackson::readValue)
        logger.info("설정 로드 완료!")
        s
    }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .followRedirects(true)
        .fastFallback(true)
        .apply {
            if (setting.useCache) {
                cache(Cache(File(setting.cacheDirectory), setting.cacheSize))
            }
        }
        .build()
    private val checkedArticle = ConcurrentHashMap.newKeySet<Int>()
    private val cachedDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val webhookClient = WebhookClientBuilder(setting.webhookUrl)
        .setHttpClient(okHttpClient)
        .build()
    private val scheduler = Scheduler()
    private var endArticleIds = ConcurrentHashMap<Int, Int>()

    private suspend fun <A> Iterable<A>.pForEach(f: suspend (A) -> Unit) = coroutineScope {
        map { async { f(it) } }.awaitAll()
    }

    init {
        JsonBrowser.setMapper(jackson)
    }

    fun start() {
        logger.info("세션 생성 중...").let {
            try {
                KotlinInside.createInstance(
                    Anonymous("", ""),
                    OkHttpInsideClient(okHttpClient),
                    true
                )
            } catch (e: NullPointerException) {
                logger.error("세션 생성 중 오류 발생", e)
                exitProcess(1)
            }
            logger.info("세션 생성 완료!")
        }

        scheduler.schedule(
            this::crawling,
            CronExpressionSchedule.parseWithSeconds(setting.cron) // ex: 0 0 * * * ? * = Every hour
        )
    }

    private fun crawling() = runBlocking {
        setting.headIds.pForEach { headId ->
            var breakLoop = false
            var page = 1
            val validator = AtomicInteger(
                if (endArticleIds.getOrDefault(headId, 0) != 0) {
                    endArticleIds.getValue(headId)
                } else {
                    -1
                }
            )
            do {
                val articleList = ArticleList(
                    gallId = setting.galleryId,
                    page = page++,
                    headId = headId
                )
                launch(cachedDispatcher) {
                    var breakSubLoop = false
                    while (!breakSubLoop) {
                        try {
                            articleList.request()
                        } catch (e: Exception) {
                            logger.error("Exception in ArticleList request", e)
                            continue
                        }
                        breakSubLoop = true
                        val gallList = articleList.getGallList()
                        if (validator.get() == -1) {
                            endArticleIds[headId] = gallList.first().identifier
                            validator.set(gallList.first().identifier)
                            continue
                        }
                        withContext(cachedDispatcher) {
                            gallList.pForEach subForEach@{
                                val endArticleId = endArticleIds.getValue(headId)
                                if (
                                    checkedArticle.contains(it.identifier) ||
                                    (it.identifier < endArticleId && validator.getAndDecrement() >= endArticleId)
                                ) {
                                    // 이미 크롤링한 글 스킵
                                    return@subForEach
                                } else if (validator.get() < endArticleId) {
                                    breakLoop = true
                                    return@subForEach
                                }
                                validator.decrementAndGet()

                                val articleRead = ArticleRead(
                                    gallId = setting.galleryId,
                                    articleId = it.identifier
                                )
                                try {
                                    articleRead.request()
                                } catch (e: Exception) {
                                    logger.error("Exception in ArticleRead request", e)
                                    return@subForEach
                                }
                                checkedArticle.add(it.identifier)

                                val articleInfo = articleRead.getViewInfo()
                                val articleMain = articleRead.getViewMain()
                                val html = StringEscapeUtils.unescapeHtml4(articleMain.content)
                                val unescapedContent = Jsoup.parse(html).text().trim()

                                webhookClient.send(
                                    WebhookEmbedBuilder().apply {
                                        setTitle(
                                            WebhookEmbed.EmbedTitle(
                                                articleInfo.subject,
                                                if (!articleList.getGallInfo().isMini) {
                                                    "https://m.dcinside.com/board/${setting.galleryId}/${articleInfo.identifier}"
                                                } else {
                                                    "https://m.dcinside.com/mini/${setting.galleryId.replaceFirst("mi$", "")}/${articleInfo.identifier}"
                                                }
                                            )
                                        )
                                        setDescription(unescapedContent)
                                    }.build()
                                )
                            }
                        }
                    }
                }
            } while (setting.breakpointMode && !breakLoop)
        }
    }
}

fun main() {
    App().start()
}
