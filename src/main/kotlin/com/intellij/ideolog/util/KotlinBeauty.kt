package com.intellij.ideolog.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.ConcurrentHashMap

private val testServiceCache = ConcurrentHashMap<Class<*>, Any>()
private const val IDEOLOG_PACKAGE_PREFIX = "com.intellij.ideolog"
private val allowedTestServices = setOf(
  "com.intellij.ideolog.highlighting.settings.LogHighlightingSettingsStore"
)

inline fun <reified T> getService(): T {
  val app = application
  val service = app?.getService(T::class.java)
  if (service != null) return service

  check(T::class.java.packageName?.startsWith(IDEOLOG_PACKAGE_PREFIX) == true) {
    "Unsupported service lookup for ${T::class.java.name}"
  }
  val ctor = T::class.java.declaredConstructors.find { it.parameterCount == 0 }
             ?: error("Service ${T::class.java.name} must have a no-arg constructor for test instantiation")
  check(ctor.canAccess(null) || ctor.trySetAccessible()) {
    "Service ${T::class.java.name} must have an accessible no-arg constructor for test instantiation"
  }
  check(allowedTestServices.contains(T::class.java.name)) {
    "Service ${T::class.java.name} is not allowed for reflective instantiation"
  }

  @Suppress("UNCHECKED_CAST")
  return testServiceCache.getOrPut(T::class.java) {
    ctor.newInstance()
  } as T
}

val application: Application? get() = ApplicationManager.getApplication()
