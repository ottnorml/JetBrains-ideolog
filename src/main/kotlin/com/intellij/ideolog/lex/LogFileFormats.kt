package com.intellij.ideolog.lex

import com.intellij.ideolog.highlighting.settings.LogParsingPattern
import com.intellij.ideolog.util.detectIdeologContext
import com.intellij.openapi.editor.Editor
import java.text.DateFormat
import java.text.ParseException
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

data class LogToken(val startOffset: Int, var endOffset: Int, val isSeparator: Boolean) {
  fun takeFrom(rawMessage: CharSequence): CharSequence {
    return rawMessage.subSequence(startOffset, endOffset)
  }
}

/**
 * Resolves a group reference (either a numeric string like "0", "1", etc. or a named group like "time") 
 * to a numeric 0-based group index by analyzing the regex pattern string.
 * Returns -1 if the group reference is invalid or not found.
 * 
 * Note: This counts ALL capturing groups in the pattern, including nested ones.
 */
fun resolveGroupReferenceToIndex(groupRef: String, patternString: String): Int {
  // Empty or whitespace-only reference means no group
  val trimmedRef = groupRef.trim()
  if (trimmedRef.isEmpty()) {
    return -1
  }
  
  // Try to parse as integer first (for numeric group references)
  val numericIndex = trimmedRef.toIntOrNull()
  if (numericIndex != null) {
    // Return as-is (already 0-based index)
    return numericIndex
  }
  
  // For named groups, parse the pattern to find the group index
  // Named groups in Java regex are: (?<name>...)
  var groupIndex = 0
  var i = 0
  while (i < patternString.length) {
    when {
      // Check for escaped characters
      patternString[i] == '\\' && i + 1 < patternString.length -> {
        i += 2 // Skip the backslash and the next character
      }
      // Check for character class
      patternString[i] == '[' -> {
        // Skip to the end of character class
        i++
        while (i < patternString.length && patternString[i] != ']') {
          if (patternString[i] == '\\' && i + 1 < patternString.length) {
            i += 2
          } else {
            i++
          }
        }
        i++ // Skip the closing ]
      }
      // Check for group start
      patternString[i] == '(' && i + 1 < patternString.length -> {
        if (patternString[i + 1] == '?') {
          // Check what kind of group this is
          if (i + 2 < patternString.length) {
            when (patternString[i + 2]) {
              '<' -> {
                // Named capturing group: (?<name>...)
                val nameStart = i + 3
                var nameEnd = nameStart
                while (nameEnd < patternString.length && patternString[nameEnd] != '>') {
                  nameEnd++
                }
                if (nameEnd < patternString.length) {
                  val groupName = patternString.substring(nameStart, nameEnd)
                  if (groupName == trimmedRef) {
                    return groupIndex
                  }
                  groupIndex++
                  i = nameEnd + 1
                } else {
                  i++
                }
              }
              ':', '=', '!' -> {
                // Non-capturing group: (?:...), positive lookahead (?=...), negative lookahead (?!...)
                // These don't increment group index
                i += 3
              }
              else -> {
                // Other special groups (e.g., (?i) for case-insensitive)
                // These typically don't capture
                i += 2
              }
            }
          } else {
            i++
          }
        } else {
          // Regular capturing group: (...)
          groupIndex++
          i++
        }
      }
      else -> i++
    }
  }
  
  // Named group not found
  return -1
}

class RegexLogParser(val uuid: UUID, val regex: Pattern, val lineRegex: Pattern, val otherParsingSettings: LogParsingPattern, val timeFormat: DateFormat)

class LogFileFormat(val myRegexLogParser: RegexLogParser?) {
  // Lazily computed and cached resolved indices for group references
  private val resolvedTimeIndex: Int by lazy {
    myRegexLogParser?.let {
      resolveGroupReferenceToIndex(
        it.otherParsingSettings.getTimeGroupReference(),
        it.otherParsingSettings.pattern
      )
    } ?: -1
  }
  
  private val resolvedSeverityIndex: Int by lazy {
    myRegexLogParser?.let {
      resolveGroupReferenceToIndex(
        it.otherParsingSettings.getSeverityGroupReference(),
        it.otherParsingSettings.pattern
      )
    } ?: -1
  }
  
  private val resolvedCategoryIndex: Int by lazy {
    myRegexLogParser?.let {
      resolveGroupReferenceToIndex(
        it.otherParsingSettings.getCategoryGroupReference(),
        it.otherParsingSettings.pattern
      )
    } ?: -1
  }
  
  fun isLineEventStart(line: CharSequence): Boolean {
    return myRegexLogParser?.lineRegex?.matcher(line)?.find() ?: (line.isNotEmpty() && !line[0].isWhitespace())
  }

  fun getTimeFieldIndex(): Int {
    // Use resolved index if available, otherwise fall back to old behavior
    return if (resolvedTimeIndex >= 0) resolvedTimeIndex else (myRegexLogParser?.otherParsingSettings?.timeColumnId ?: 0)
  }

  fun tokenize(event: CharSequence, output: MutableList<LogToken>, onlyValues: Boolean = false) {
    if(myRegexLogParser == null) {
      LogFileLexer.lexPlainLog(event, output, onlyValues)
    } else {
      LogFileLexer.lexRegex(event, output, onlyValues, myRegexLogParser)
    }
  }

  fun extractDate(tokens: List<LogToken>): LogToken? {
    val idx = if (resolvedTimeIndex >= 0) resolvedTimeIndex else (myRegexLogParser?.otherParsingSettings?.timeColumnId ?: return null)
    if(tokens.size > idx)
      return tokens.asSequence().filter { !it.isSeparator }.elementAtOrNull(idx)
    return null
  }

  fun extractSeverity(tokens: List<LogToken>): LogToken? {
    val idx = if (resolvedSeverityIndex >= 0) resolvedSeverityIndex else (myRegexLogParser?.otherParsingSettings?.severityColumnId ?: return null)
    if(tokens.size > idx)
      return tokens.asSequence().filter { !it.isSeparator }.elementAtOrNull(idx)
    return null
  }

  fun extractCategory(tokens: List<LogToken>): LogToken? {
    val idx = if (resolvedCategoryIndex >= 0) resolvedCategoryIndex else (myRegexLogParser?.otherParsingSettings?.categoryColumnId ?: return null)
    if(tokens.size > idx)
      return tokens.asSequence().filter { !it.isSeparator }.elementAtOrNull(idx)
    return null
  }

  fun extractMessage(tokens: List<LogToken>): LogToken {
    return tokens.last { !it.isSeparator }
  }

  fun validateFormatUUID(uuid: UUID?): Boolean =
    uuid == null || myRegexLogParser?.uuid == uuid


  fun parseLogEventTimeSeconds(time: CharSequence): Long? {
    return myRegexLogParser?.let {
      try {
        return@let it.timeFormat.parse(time.toString()).time
      } catch (_: ParseException) {
        // silently ignore it
      } catch (_: NumberFormatException) {

      } catch (_: ArrayIndexOutOfBoundsException) {
        // apparently this one is also randomly thrown by parsing
      }
      return@let null
    }
  }
}

fun detectLogFileFormat(editor: Editor): LogFileFormat = detectIdeologContext(editor).detectLogFileFormat()

fun detectLogFileFormat(editor: Editor, offset: Int): LogFileFormat = detectIdeologContext(editor).detectLogFileFormat(offset)
