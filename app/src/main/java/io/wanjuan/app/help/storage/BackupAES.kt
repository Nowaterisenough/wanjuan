package io.wanjuan.app.help.storage

import cn.hutool.crypto.symmetric.AES
import io.wanjuan.app.help.config.LocalConfig
import io.wanjuan.app.utils.MD5Utils

class BackupAES : AES(
    MD5Utils.md5Encode(LocalConfig.password ?: "").encodeToByteArray(0, 16)
)