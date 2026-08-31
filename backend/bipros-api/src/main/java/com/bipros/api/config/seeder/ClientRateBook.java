package com.bipros.api.config.seeder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The client's rate book — generated once from "Requirements final - 01 Aug 2026.xlsx",
 * Rates sheet, with the owner-approved cleanups applied at generation time (overhead rows
 * excluded, typos fixed, duplicate Plumber merged, first-wins duplicate material codes,
 * case-only unit normalization). The JSON is the reviewable source of truth; rates are
 * pre-rounded to 4 dp (the DB column scale).
 *
 * <p>Manpower {@code salaryPerMonth} is MONTHLY — {@link ClientRateBookSeeder} derives the
 * daily rate as {@code salary / 26} (the sheet's own convention: its plant daily rates are
 * visibly monthly/26). All numeric values are strings so no float artifacts sneak in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientRateBook(
    int version,
    String source,
    String notes,
    List<MaterialRow> materials,
    List<EquipmentRow> equipment,
    List<ManpowerRole> manpower) {

  static final String RESOURCE_PATH = "seed/client-rate-book.json";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MaterialRow(String code, String name, String unit, String rate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EquipmentRow(String code, String name, String ratePerDay, String operatorCode) {}

  /**
   * {@code note} (v2, Web sheet row 4): set on entries whose salary is an ESTIMATE rather
   * than a client rate-sheet figure. It replaces the "Client rate book." prefix in the seeded
   * role description so admins can see the rate needs confirming — the description must never
   * attribute an invented number to the client.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManpowerRole(String code, String title, List<ManpowerVariant> variants,
                             String note) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ManpowerVariant(String lCode, String category, String salaryPerMonth) {}

  public static ClientRateBook load(ObjectMapper objectMapper) throws IOException {
    try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
      return objectMapper.readValue(in, ClientRateBook.class);
    }
  }
}
