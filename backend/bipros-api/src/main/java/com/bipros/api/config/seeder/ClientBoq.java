package com.bipros.api.config.seeder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The client's contract BOQ — generated once from "Requirements final - 01 Aug 2026.xlsx"
 * (BOQ sheet = 551 line items after excluding the struck row 13.1.7(i)b "deleted";
 * BOQ1 sheet = 9 weighted splits), owner decisions 2026-08-12: default quantities
 * (LS/PS/Included = 1, monthly = 12, measured = 100 — the workbook has no quantity column),
 * duplicate item numbers suffixed "-2", split weights are engineering estimates with the
 * measurement operation on the billable-quantity step. The JSON is the reviewable source of
 * truth; all numerics are strings (no float artifacts) and rates are pre-rounded to 4 dp.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientBoq(
    int version,
    String source,
    String notes,
    List<String> targetProjectCodes,
    List<Item> items,
    List<Split> splits) {

  static final String RESOURCE_PATH = "seed/client-boq.json";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(String itemNo, String description, String unit, String chapter,
                     String qty, String rate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Split(String itemNo, String mode, List<Op> operations) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Op(String code, String name, String weight, boolean measure) {}

  public static ClientBoq load(ObjectMapper objectMapper) throws IOException {
    try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
      return objectMapper.readValue(in, ClientBoq.class);
    }
  }
}
