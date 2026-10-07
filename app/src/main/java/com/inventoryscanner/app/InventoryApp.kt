package com.inventoryscanner.app

import android.app.Application
import com.inventoryscanner.app.data.AppDb

class InventoryApp : Application() {
    val db by lazy { AppDb.get(this) }
}
