package com.intellij.ideolog.lex

import junit.framework.TestCase

class GroupReferenceResolutionTest : TestCase() {

  fun testResolveNumericGroupReference() {
    val pattern = "^([^|]*)\\|([^|]*)\\|([^|]*)\\|(.*)$"
    
    assertEquals(0, resolveGroupReferenceToIndex("0", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("1", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("2", pattern))
    assertEquals(3, resolveGroupReferenceToIndex("3", pattern))
  }

  fun testResolveNamedGroupReference() {
    val pattern = "^(?<time>[^|]*)\\|(?<severity>[^|]*)\\|(?<category>[^|]*)\\|(?<message>.*)$"
    
    assertEquals(0, resolveGroupReferenceToIndex("time", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("severity", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("category", pattern))
    assertEquals(3, resolveGroupReferenceToIndex("message", pattern))
  }

  fun testResolveMixedGroupReferences() {
    // Pattern with both named and unnamed groups
    val pattern = "^(?<time>[^\\[]+)(\\[[\\s\\d]+])\\s*(?<severity>\\w*)\\s*-\\s*(?<category>\\S*)\\s*-(.+)$"
    
    // Named groups
    assertEquals(0, resolveGroupReferenceToIndex("time", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("severity", pattern))
    assertEquals(3, resolveGroupReferenceToIndex("category", pattern))
    
    // Numeric references
    assertEquals(0, resolveGroupReferenceToIndex("0", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("1", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("2", pattern))
    assertEquals(3, resolveGroupReferenceToIndex("3", pattern))
    assertEquals(4, resolveGroupReferenceToIndex("4", pattern))
  }

  fun testResolveGroupWithNonCapturingGroups() {
    // Pattern with non-capturing groups (?:...)
    val pattern = "^(?<time>\\d+)(?::(?<severity>\\w+))?\\s*(?<message>.*)$"
    
    assertEquals(0, resolveGroupReferenceToIndex("time", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("severity", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("message", pattern))
  }

  fun testResolveGroupWithLookahead() {
    // Pattern with positive lookahead (?=...) and negative lookahead (?!...)
    val pattern = "^(?<time>\\d+)(?=\\s)(?!ERROR)(?<message>.*)$"
    
    assertEquals(0, resolveGroupReferenceToIndex("time", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("message", pattern))
  }

  fun testInvalidGroupReference() {
    val pattern = "^(?<time>[^|]*)\\|(?<severity>[^|]*)\\|(.*)$"
    
    // Non-existent named group
    assertEquals(-1, resolveGroupReferenceToIndex("nonexistent", pattern))
    
    // Empty string
    assertEquals(-1, resolveGroupReferenceToIndex("", pattern))
    
    // Whitespace only
    assertEquals(-1, resolveGroupReferenceToIndex("   ", pattern))
  }

  fun testComplexRegexWithEscapes() {
    val pattern = "^(?<time>\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s)\\|(?<severity>\\s[A-Z]*\\s*)\\|(?<category>\\s.+:.+:\\d+\\s-\\s.*)$"
    
    assertEquals(0, resolveGroupReferenceToIndex("time", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("severity", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("category", pattern))
  }

  fun testGroupReferenceWithCharacterClass() {
    // Pattern with character classes
    val pattern = "^(?<date>[\\d-]+)\\s+(?<time>[\\d:]+)\\s+(?<level>[A-Z]+)\\s+(?<message>.*)$"
    
    assertEquals(0, resolveGroupReferenceToIndex("date", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("time", pattern))
    assertEquals(2, resolveGroupReferenceToIndex("level", pattern))
    assertEquals(3, resolveGroupReferenceToIndex("message", pattern))
  }

  fun testWhitespaceInGroupReference() {
    val pattern = "^(?<time>[^|]*)\\|(?<severity>[^|]*)\\|(.*)$"
    
    // Whitespace should be trimmed
    assertEquals(0, resolveGroupReferenceToIndex(" time ", pattern))
    assertEquals(1, resolveGroupReferenceToIndex("  severity  ", pattern))
  }
}
