package com.intellij.ideolog.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.ConcurrentHashMap

private val testServiceCache = ConcurrentHashMap<Class<*>, Any>()
private const val IDEOLOG_PACKAGE_PREFIX = "com.intellij.ideolog"

inline fun <reified T> getService(): T {
  val app = application
  val service = app?.getService(T::class.java)
  if (service != null) return service

  check(T::class.java.packageName?.startsWith(IDEOLOG_PACKAGE_PREFIX) == true) {
    "Unsupported service lookup for ${T::class.java.name}"
  }
  check(T::class.java.declaredConstructors.any { it.parameterCount == 0 }) {
    "Service ${T::class.java.name} must have an accessible no-arg constructor for test instantiation"
  }

  @Suppress("UNCHECKED_CAST")
  return testServiceCache.getOrPut(T::class.java) {
    T::class.java.getDeclaredConstructor().newInstance()
  } as T
}

val application: Application? get() = ApplicationManager.getApplication()
