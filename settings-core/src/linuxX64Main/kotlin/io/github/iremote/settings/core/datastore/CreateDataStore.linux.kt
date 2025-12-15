package io.github.iremote.settings.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
fun createSettingsDataStore(name: String): DataStore<Preferences> =
    createDataStore(
        producePath = {
            val home = getenv("HOME")?.toKString() ?: "/tmp"
            "$home/.config/$name.preferences_pb"
        }
    )