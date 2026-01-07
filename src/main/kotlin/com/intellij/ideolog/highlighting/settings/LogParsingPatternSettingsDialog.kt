package com.intellij.ideolog.highlighting.settings

import com.intellij.ideolog.IdeologBundle
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorTextField
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import net.miginfocom.swing.MigLayout
import org.intellij.lang.regexp.RegExpFileType
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class LogParsingPatternSettingsDialog(private val item: LogParsingPattern) : DialogWrapper(null, true, IdeModalityType.IDE) {
  private var myNameText: EditorTextField? = null
  private var myParsingPatternText: EditorTextField? = null
  private var myLineStartPatternText: EditorTextField? = null
  private var myTimePatternText: EditorTextField? = null

  private var myTimeColumnIdText: EditorTextField? = null
  private var mySeverityColumnIdText: EditorTextField? = null
  private var myCategoryColumnIdText: EditorTextField? = null

  init {
    init()
    initValidation()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(MigLayout("fill, wrap 2", "[right][fill]"))

    val wikiCustomLogFormats = BorderLayoutPanel().apply {
      add(HyperlinkLabel().apply {
        setHyperlinkText(IdeologBundle.message("link.wiki.custom.log.format"))
        setHyperlinkTarget("https://github.com/JetBrains/ideolog/wiki/Custom-Log-Formats")
      })
    }
    panel.add(wikiCustomLogFormats, "span 2, left")

    panel.add(JLabel(IdeologBundle.message("settings.dialog.label.name")))
    val nameText = EditorTextField(item.name)
    myNameText = nameText
    panel.add(nameText)

    panel.add(JLabel(IdeologBundle.message("settings.dialog.label.message.pattern")))
    val patternText = EditorTextField(item.pattern, ProjectManager.getInstance().defaultProject, RegExpFileType.INSTANCE)
    myParsingPatternText = patternText
    panel.add(patternText)

    panel.add(JLabel(IdeologBundle.message("settings.dialog.label.message.start.pattern")))
    val linePatternText = EditorTextField(item.lineStartPattern, ProjectManager.getInstance().defaultProject, RegExpFileType.INSTANCE)
    myLineStartPatternText = linePatternText
    panel.add(linePatternText)

    panel.add(JLabel(IdeologBundle.message("settings.dialog.label.time.format")))
    val timeFormatText = EditorTextField(item.timePattern)
    myTimePatternText = timeFormatText
    panel.add(timeFormatText)
    panel.add(
      JLabel(IdeologBundle.message("settings.dialog.label.preview")).apply {
        foreground = UIUtil.getLabelDisabledForeground()
      }
    )
    val timeFormatPreviewLabel = JLabel(getDatePreviewText(item.timePattern))
    val queue = MergingUpdateQueue("TimePreview", 500, true, MergingUpdateQueue.ANY_COMPONENT, myDisposable)
    timeFormatText.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        queue.queue(object : Update("typingTime") {
          override fun run() {
            timeFormatPreviewLabel.text = getDatePreviewText(event.document.text)
          }
        })
      }
    }, myDisposable)
    panel.add(timeFormatPreviewLabel)
    panel.add(JLabel())

    val linkLbl = BorderLayoutPanel().apply {
      addToLeft(HyperlinkLabel().apply {
        setHyperlinkText(IdeologBundle.message("link.label.documentation"))
        setHyperlinkTarget("https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")
      })
    }
    panel.add(linkLbl)


    panel.add(JLabel(IdeologBundle.message("time.capture.group")))
    val timeText = EditorTextField(item.getTimeGroupReference())
    myTimeColumnIdText = timeText
    panel.add(timeText)

    panel.add(JLabel(IdeologBundle.message("severity.capture.group")))
    val severityText = EditorTextField(item.getSeverityGroupReference())
    mySeverityColumnIdText = severityText
    panel.add(severityText)

    panel.add(JLabel(IdeologBundle.message("category.capture.group")))
    val categoryText = EditorTextField(item.getCategoryGroupReference())
    myCategoryColumnIdText = categoryText
    panel.add(categoryText)

    return panel
  }

  private fun getDatePreviewText(format: String): String {
    val parsedFormat = try {
      SimpleDateFormat(format).format(Date(629518620000))
    }
    catch (_: Throwable) { "" }

    return parsedFormat.ifBlank { "-" }
  }

  /**
   * Helper function to update group reference fields in the pattern.
   * If the text is a valid integer, updates the old integer field and clears the string field.
   * Otherwise, stores as a named group reference.
   */
  private fun updateGroupReference(
    text: String,
    setIntField: (Int) -> Unit,
    setStringField: (String?) -> Unit
  ) {
    val trimmed = text.trim()
    val numValue = trimmed.toIntOrNull()
    if (numValue != null) {
      // If it's a valid integer, update the old field for backward compatibility
      setIntField(numValue)
      setStringField(null) // Clear the string ref when using integer
    } else {
      // It's a named group, store in the new field
      setStringField(trimmed)
      setIntField(-1) // Keep the old field at -1 to indicate it's not used
    }
  }

  override fun doOKAction() {
    myNameText?.let { item.name = it.text }
    myParsingPatternText?.let { item.pattern = it.text }
    myLineStartPatternText?.let { item.lineStartPattern = it.text }
    myTimePatternText?.let { item.timePattern = it.text }

    // Handle group references
    myTimeColumnIdText?.let { 
      updateGroupReference(it.text, { item.timeColumnId = it }, { item.timeGroupRef = it })
    }
    
    mySeverityColumnIdText?.let { 
      updateGroupReference(it.text, { item.severityColumnId = it }, { item.severityGroupRef = it })
    }
    
    myCategoryColumnIdText?.let { 
      updateGroupReference(it.text, { item.categoryColumnId = it }, { item.categoryGroupRef = it })
    }

    if (DefaultSettingsStoreItems.ParsingPatternsUUIDs.contains(item.uuid)) {
      item.uuid = UUID.randomUUID()
    }

    super.doOKAction()
  }

  override fun doValidateAll(): MutableList<ValidationInfo> {
    val results = ArrayList<ValidationInfo>()

    var patternText: String? = null
    try {
      myParsingPatternText?.let { 
        patternText = it.text
        Pattern.compile(patternText)
      }
    } catch(e : PatternSyntaxException) {
      results.add(ValidationInfo(e.localizedMessage, myParsingPatternText))
    }

    try {
      myLineStartPatternText?.let { Pattern.compile(it.text) }
    } catch(e : PatternSyntaxException) {
      results.add(ValidationInfo(e.localizedMessage, myLineStartPatternText))
    }

    try {
      myTimePatternText?.let { SimpleDateFormat(it.text) }
    } catch(e : IllegalArgumentException) {
      results.add(ValidationInfo(e.localizedMessage, myTimePatternText))
    }

    // Validate group references if pattern is valid
    if (patternText != null && results.none { it.component == myParsingPatternText }) {
      myTimeColumnIdText?.let { field ->
        val ref = field.text.trim()
        if (ref.isNotEmpty()) {
          val resolvedIndex = com.intellij.ideolog.lex.resolveGroupReferenceToIndex(ref, patternText!!)
          if (resolvedIndex < 0) {
            results.add(ValidationInfo("Group reference '$ref' not found in pattern", field))
          }
        }
      }
      
      mySeverityColumnIdText?.let { field ->
        val ref = field.text.trim()
        if (ref.isNotEmpty()) {
          val resolvedIndex = com.intellij.ideolog.lex.resolveGroupReferenceToIndex(ref, patternText!!)
          if (resolvedIndex < 0) {
            results.add(ValidationInfo("Group reference '$ref' not found in pattern", field))
          }
        }
      }
      
      myCategoryColumnIdText?.let { field ->
        val ref = field.text.trim()
        if (ref.isNotEmpty()) {
          val resolvedIndex = com.intellij.ideolog.lex.resolveGroupReferenceToIndex(ref, patternText!!)
          if (resolvedIndex < 0) {
            results.add(ValidationInfo("Group reference '$ref' not found in pattern", field))
          }
        }
      }
    }

    return results
  }
}
