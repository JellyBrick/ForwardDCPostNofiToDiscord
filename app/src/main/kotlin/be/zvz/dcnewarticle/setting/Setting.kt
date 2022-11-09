package be.zvz.dcnewarticle.setting

data class Setting(
    val galleryId: String,
    val cron: String,
    val webhookUrl: String,
    val headIds: List<Int>
)
