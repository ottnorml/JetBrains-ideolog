package com.intellij.ideolog.lex

import com.intellij.ideolog.highlighting.LogEvent
import com.intellij.ideolog.highlighting.settings.LogParsingPattern
import junit.framework.TestCase
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Constant indicating that a group reference is not being used
 */
private const val UNUSED_GROUP_ID = -1

class NamedGroupExtractionTest : TestCase() {

  fun testExtractFieldsUsingNamedGroups() {
    // Create a pattern using named groups
    val pattern = LogParsingPattern(
      enabled = true,
      name = "Test Named Groups",
      pattern = "^(?<ts>[^|]*)\\|(?<sev>[^|]*)\\|(?<cat>[^|]*)\\|(?<msg>.*)$",
      timePattern = "HH:mm:ss.SSS",
      lineStartPattern = "^\\d",
      timeColumnId = UNUSED_GROUP_ID,  // Not used when named groups are specified
      severityColumnId = UNUSED_GROUP_ID,
      categoryColumnId = UNUSED_GROUP_ID,
      uuid = UUID.randomUUID(),
      timeGroupRef = "ts",
      severityGroupRef = "sev",
      categoryGroupRef = "cat"
    )
    
    val format = LogFileFormat(
      RegexLogParser(
        pattern.uuid,
        Pattern.compile(pattern.pattern, Pattern.DOTALL),
        Pattern.compile(pattern.lineStartPattern),
        pattern,
        SimpleDateFormat(pattern.timePattern)
      )
    )
    
    val event = LogEvent(
      "12:34:56.789|ERROR|DatabaseModule|Connection failed",
      0,
      format
    )
    
    assertEquals("12:34:56.789", event.date)
    assertEquals("ERROR", event.level)
    assertEquals("DatabaseModule", event.category)
    assertEquals("Connection failed", event.message)
  }

  fun testExtractFieldsUsingNumericGroupsAsStrings() {
    // Create a pattern using numeric group references as strings
    val pattern = LogParsingPattern(
      enabled = true,
      name = "Test Numeric Groups",
      pattern = "^([^|]*)\\|([^|]*)\\|([^|]*)\\|(.*)$",
      timePattern = "HH:mm:ss.SSS",
      lineStartPattern = "^\\d",
      timeColumnId = 0,
      severityColumnId = 1,
      categoryColumnId = 2,
      uuid = UUID.randomUUID(),
      timeGroupRef = "0",     // Numeric reference as string
      severityGroupRef = "1",
      categoryGroupRef = "2"
    )
    
    val format = LogFileFormat(
      RegexLogParser(
        pattern.uuid,
        Pattern.compile(pattern.pattern, Pattern.DOTALL),
        Pattern.compile(pattern.lineStartPattern),
        pattern,
        SimpleDateFormat(pattern.timePattern)
      )
    )
    
    val event = LogEvent(
      "12:34:56.789|WARN|AuthModule|Invalid credentials",
      0,
      format
    )
    
    assertEquals("12:34:56.789", event.date)
    assertEquals("WARN", event.level)
    assertEquals("AuthModule", event.category)
    assertEquals("Invalid credentials", event.message)
  }

  fun testBackwardCompatibilityWithOldIntegerGroupIds() {
    // Create a pattern using the old integer-based approach (no string refs)
    val pattern = LogParsingPattern(
      enabled = true,
      name = "Test Old Format",
      pattern = "^([^|]*)\\|([^|]*)\\|([^|]*)\\|(.*)$",
      timePattern = "HH:mm:ss.SSS",
      lineStartPattern = "^\\d",
      timeColumnId = 0,
      severityColumnId = 1,
      categoryColumnId = 2,
      uuid = UUID.randomUUID(),
      timeGroupRef = null,   // No named refs, should fall back to integer IDs
      severityGroupRef = null,
      categoryGroupRef = null
    )
    
    val format = LogFileFormat(
      RegexLogParser(
        pattern.uuid,
        Pattern.compile(pattern.pattern, Pattern.DOTALL),
        Pattern.compile(pattern.lineStartPattern),
        pattern,
        SimpleDateFormat(pattern.timePattern)
      )
    )
    
    val event = LogEvent(
      "12:34:56.789|INFO|AppModule|Application started",
      0,
      format
    )
    
    assertEquals("12:34:56.789", event.date)
    assertEquals("INFO", event.level)
    assertEquals("AppModule", event.category)
    assertEquals("Application started", event.message)
  }

  fun testMixedNamedAndUnnamedGroups() {
    // Pattern with both named and unnamed groups
    val pattern = LogParsingPattern(
      enabled = true,
      name = "Test Mixed Groups",
      pattern = "^(?<time>[^\\[]+)(\\[[\\s\\d]+])\\s*(?<severity>\\w*)\\s*-\\s*(?<category>\\S*)\\s*-(.+)$",
      timePattern = "yyyy-MM-dd HH:mm:ss,SSS",
      lineStartPattern = "^\\d",
      timeColumnId = UNUSED_GROUP_ID,
      severityColumnId = UNUSED_GROUP_ID,
      categoryColumnId = UNUSED_GROUP_ID,
      uuid = UUID.randomUUID(),
      timeGroupRef = "time",
      severityGroupRef = "severity",
      categoryGroupRef = "category"
    )
    
    val format = LogFileFormat(
      RegexLogParser(
        pattern.uuid,
        Pattern.compile(pattern.pattern, Pattern.DOTALL),
        Pattern.compile(pattern.lineStartPattern),
        pattern,
        SimpleDateFormat(pattern.timePattern)
      )
    )
    
    val event = LogEvent(
      "2023-05-02 23:09:07,110 [    142]   INFO - #c.i.i.StartupUtil - JVM started",
      0,
      format
    )
    
    assertEquals("2023-05-02 23:09:07,110", event.date)
    assertEquals("INFO", event.level)
    assertEquals("#c.i.i.StartupUtil", event.category)
    assertEquals("JVM started", event.message)
  }

  fun testNamedGroupsWithOptionalMatches() {
    // Pattern where some groups might not match
    val pattern = LogParsingPattern(
      enabled = true,
      name = "Test Optional Groups",
      pattern = "^(?<time>\\d+:\\d+:\\d+\\.\\d+)(\\|(?<severity>\\w+))?(\\|(?<category>\\w+))?\\|(?<message>.*)$",
      timePattern = "HH:mm:ss.SSS",
      lineStartPattern = "^\\d",
      timeColumnId = UNUSED_GROUP_ID,
      severityColumnId = UNUSED_GROUP_ID,
      categoryColumnId = UNUSED_GROUP_ID,
      uuid = UUID.randomUUID(),
      timeGroupRef = "time",
      severityGroupRef = "severity",
      categoryGroupRef = "category"
    )
    
    val format = LogFileFormat(
      RegexLogParser(
        pattern.uuid,
        Pattern.compile(pattern.pattern, Pattern.DOTALL),
        Pattern.compile(pattern.lineStartPattern),
        pattern,
        SimpleDateFormat(pattern.timePattern)
      )
    )
    
    // Test with all groups present
    val event1 = LogEvent(
      "12:34:56.789|ERROR|Module|Message",
      0,
      format
    )
    assertEquals("12:34:56.789", event1.date)
    assertEquals("ERROR", event1.level)
    assertEquals("Module", event1.category)
    assertEquals("Message", event1.message)
    
    // Test with only time and message
    val event2 = LogEvent(
      "12:34:56.789|Simple message",
      0,
      format
    )
    assertEquals("12:34:56.789", event2.date)
    assertNull(event2.level)  // Optional group didn't match
    assertNull(event2.category)  // Optional group didn't match
    assertEquals("Simple message", event2.message)
  }
}
