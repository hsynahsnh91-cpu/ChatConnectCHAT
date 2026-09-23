package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TypingStateDispatcher {
    private val _typingMap = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingMap: StateFlow<Map<String, Set<String>>> = _typingMap

    fun setTyping(chatId: String, userId: String, isTyping: Boolean) {
        val currentMap = _typingMap.value.toMutableMap()
        val currentSet = currentMap[chatId]?.toMutableSet() ?: mutableSetOf()
        if (isTyping) {
            currentSet.add(userId)
        } else {
            currentSet.remove(userId)
        }
        currentMap[chatId] = currentSet
        _typingMap.value = currentMap
    }

    fun isUserTyping(chatId: String, userId: String): Boolean {
        return _typingMap.value[chatId]?.contains(userId) == true
    }
}
