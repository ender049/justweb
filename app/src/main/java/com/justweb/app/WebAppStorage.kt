package com.justweb.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WebAppStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("justweb_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<WebApp> {
        val json = prefs.getString(KEY_APPS, "[]") ?: "[]"
        val type = object : TypeToken<List<WebApp>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun save(app: WebApp) {
        val apps = getAll().toMutableList()
        val existing = apps.indexOfFirst { it.id == app.id }
        if (existing >= 0) {
            apps[existing] = app
        } else {
            apps.add(app)
        }
        saveAll(apps)
    }

    fun delete(id: String) {
        val apps = getAll().toMutableList()
        apps.removeAll { it.id == id }
        saveAll(apps)
    }

    fun getById(id: String): WebApp? {
        return getAll().find { it.id == id }
    }

    private fun saveAll(apps: List<WebApp>) {
        val json = gson.toJson(apps)
        prefs.edit().putString(KEY_APPS, json).apply()
    }

    companion object {
        private const val KEY_APPS = "apps"
    }
}