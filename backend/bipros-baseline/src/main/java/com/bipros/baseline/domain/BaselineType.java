package com.bipros.baseline.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BaselineType {
  PROJECT,
  PRIMARY,
  SECONDARY,
  TERTIARY;

  @JsonCreator
  public static BaselineType fromString(String value) {
    if (value == null) return null;
    String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    return switch (n) {
      case "PROJECT", "PROJECT_BASELINE" -> PROJECT;
      case "PRIMARY", "PRIMARY_BASELINE" -> PRIMARY;
      case "SECONDARY", "SECONDARY_BASELINE" -> SECONDARY;
      case "TERTIARY", "TERTIARY_BASELINE" -> TERTIARY;
      default -> throw new IllegalArgumentException(
          "Unknown BaselineType '" + value + "' (valid: PROJECT, PRIMARY, SECONDARY, TERTIARY)");
    };
  }
}
