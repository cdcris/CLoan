package org.example.project

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.example.project.db.Database

private val database by lazy { Database(NativeSqliteDriver(Database.Schema, "cloan.db")) }

fun MainViewController() = ComposeUIViewController { App(database) }
