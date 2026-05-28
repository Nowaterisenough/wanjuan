package io.wanjuan.app.utils.canvasrecorder.pools

import android.graphics.Picture
import io.wanjuan.app.utils.objectpool.BaseObjectPool

class PicturePool : BaseObjectPool<Picture>(64) {

    override fun create(): Picture = Picture()

}
