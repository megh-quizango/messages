package com.text.messages.sms.messanger.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.text.messages.sms.messanger.data.model.CustomFilter

object CustomFilterStorage {
    private const val PREFS_NAME = "custom_filters"
    private const val KEY_FILTERS = "filters_list"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveFilters(context: Context, filters: List<CustomFilter>) {
        val prefs = getSharedPreferences(context)
        val gson = Gson()
        val json = gson.toJson(filters)
        prefs.edit().putString(KEY_FILTERS, json).apply()
    }
    
    fun loadFilters(context: Context): List<CustomFilter> {
        val prefs = getSharedPreferences(context)
        val json = prefs.getString(KEY_FILTERS, null)
        if (json == null) {
            return emptyList()
        }
        val gson = Gson()
        val type = object : TypeToken<List<CustomFilter>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    fun addFilter(context: Context, filter: CustomFilter) {
        val filters = loadFilters(context).toMutableList()
        filters.add(filter)
        saveFilters(context, filters)
    }
    
    fun updateFilter(context: Context, filter: CustomFilter) {
        val filters = loadFilters(context).toMutableList()
        val index = filters.indexOfFirst { it.id == filter.id }
        if (index >= 0) {
            filters[index] = filter
            saveFilters(context, filters)
        }
    }
    
    fun deleteFilter(context: Context, filterId: String) {
        val filters = loadFilters(context).toMutableList()
        filters.removeAll { it.id == filterId }
        saveFilters(context, filters)
    }
    
    fun getFilter(context: Context, filterId: String): CustomFilter? {
        return loadFilters(context).find { it.id == filterId }
    }
    
    fun addConversationToFilter(context: Context, filterId: String, threadId: Long) {
        val filter = getFilter(context, filterId) ?: return
        if (!filter.threadIds.contains(threadId)) {
            filter.threadIds.add(threadId)
            updateFilter(context, filter)
        }
    }
    
    fun removeConversationFromFilter(context: Context, filterId: String, threadId: Long) {
        val filter = getFilter(context, filterId) ?: return
        filter.threadIds.remove(threadId)
        updateFilter(context, filter)
    }
}

