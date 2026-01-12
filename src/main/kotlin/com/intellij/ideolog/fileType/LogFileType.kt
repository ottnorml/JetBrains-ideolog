package com.intellij.ideolog.fileType

import com.intellij.ideolog.IdeologBundle
import com.intellij.ideolog.file.LogIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import java.util.concurrent.atomic.AtomicBoolean

object LogFileType : com.intellij.openapi.fileTypes.LanguageFileType(LogLanguage) {
  init {
    ensureAssociation()
    ApplicationManager.getApplication()?.invokeLater { ensureAssociation() }
  }
  private val associationEnsured = AtomicBoolean(false)
  private fun ensureAssociation() {
    val application = ApplicationManager.getApplication() ?: return
    if (associationEnsured.compareAndSet(false, true)) {
      FileTypeManager.getInstance().associatePattern(this, "*.log")
    }
  }
  override fun getName(): String {
    return "Log"
  }
  override fun getDescription(): String = IdeologBundle.message("log.files")
  override fun getDefaultExtension(): String = "log"
  override fun getIcon(): javax.swing.Icon = LogIcons.LogFile
}
