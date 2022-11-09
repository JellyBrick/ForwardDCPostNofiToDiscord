package be.zvz.dcnewarticle.setting

data class Setting(
    val galleryId: String,
    val cron: String,
    val webhookUrl: String,
    val headIds: List<Int>,
    val breakpointMode: Boolean = false,
    val useCache: Boolean = false,
    val cacheDirectory: String = "cache",
    val cacheSize: Long = -1
)
