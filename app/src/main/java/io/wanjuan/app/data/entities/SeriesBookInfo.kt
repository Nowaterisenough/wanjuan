package io.wanjuan.app.data.entities

/** Lightweight shelf snapshot. The local URL is used only to apply a validated assignment. */
data class SeriesBookInfo(val bookUrl: String, val name: String, val author: String, val summary: String?, val seriesName: String?)
