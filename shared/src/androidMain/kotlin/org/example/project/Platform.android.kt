package org.example.project

import android.os.Build
import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.example.project.db.Database
import java.util.Calendar

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

fun createDatabase(context: Context, databaseName: String = "cloan.db"): Database =
    Database(AndroidSqliteDriver(Database.Schema, context.applicationContext, databaseName))

actual fun currentDateIso(): String {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR).toString().padStart(4, '0')
    val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "$year-$month-$day"
}
