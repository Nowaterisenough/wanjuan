package io.wanjuan.app.ui.book.import.remote

import android.app.Application
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Server

class ServersViewModel(application: Application): BaseViewModel(application) {


    fun delete(server: Server) {
        execute {
            appDb.serverDao.delete(server)
        }
    }

}