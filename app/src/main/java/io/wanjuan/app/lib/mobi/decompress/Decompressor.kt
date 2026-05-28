package io.wanjuan.app.lib.mobi.decompress

interface Decompressor {

    fun decompress(data: ByteArray): ByteArray

}
