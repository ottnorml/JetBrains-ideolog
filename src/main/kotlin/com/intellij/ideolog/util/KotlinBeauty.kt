package com.intellij.ideolog.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.ConcurrentHashMap

private val testServiceCache = ConcurrentHashMap<Class<*>, Any>()

inline fun <reified T> getService(): T {
  val app = application
  val service = app?.getService(T::class.java)
  if (service != null) return service

  @Suppress("UNCHECKED_CAST")
  return testServiceCache.getOrPut(T::class.java) {
    T::class.java.getDeclaredConstructor().newInstance()
  } as T
}

val application: Application? get() = ApplicationManager.getApplication()
