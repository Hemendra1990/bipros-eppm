package com.bipros.integration.adapter.satellite;

import com.bipros.common.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel Hub (Copernicus) adapter.
 * <p>
 * Uses three Sentinel Hub endpoints:
 * <ul>
 *   <li>{@code POST /oauth/token} — client-credentials OAuth2 flow. Cached 55 min.</li>
 *   <li>{@code POST /api/v1/catalog/1.0.0/search} — STAC-style scene listing by
 *       AOI + date range. Returns a JSON FeatureCollection; we turn each feature
 *       into a {@link SceneDescriptor}.</li>
 *   <li>{@code POST /api/v1/process} — produces a GeoTIFF/PNG for one scene
 *       clipped to the AOI using an inline evalscript that returns true-colour
 *       RGB from Sentinel-2 bands B02/B03/B04.</li>
 * </ul>
 * <p>
 * Active when {@code bipros.satellite.sentinel-hub.enabled=true} AND a client-id
 * is configured. Dev default is {@code enabled=false} so developers without
 * Sentinel Hub credentials can still boot the app.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "bipros.satellite.sentinel-hub.enabled", havingValue = "true")
public class SentinelHubAdapter implements SatelliteAdapter {

    private static final String VENDOR_ID = "sentinel-hub";
    /** True-colour evalscript — S2 bands B04 (red), B03 (green), B02 (blue). */
    private static final String EVAL_SCRIPT = """
        //VERSION=3
        function setup() {
          return { input: ["B04","B03","B02"], output: { bands: 3, sampleType: "AUTO" } };
        }
        function evaluatePixel(s) { return [s.B04*2.5, s.B03*2.5, s.B02*2.5]; }
        """;

    private final RestClient http;
    private final RestClient tokenHttp;
    private final ObjectMapper json;
    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;
    private final String tokenUrl;
    private final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();

    // Cached OAuth2 bearer — refreshed when within 5 min of expiry.
    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public SentinelHubAdapter(
        ObjectMapper objectMapper,
        // Default base URL is Copernicus Data Space Ecosystem (CDSE). CDSE-issued
        // OAuth clients (free tier created at dataspace.copernicus.eu) work only
        // against sh.dataspace.copernicus.eu; the classic services.sentinel-hub.com
        // requires a separate legacy account. Override via
        // bipros.satellite.sentinel-hub.base-url for legacy credentials.
        @Value("${bipros.satellite.sentinel-hub.base-url:https://sh.dataspace.copernicus.eu}") String baseUrl,
        // CDSE's OAuth runs on a separate identity service (Keycloak). The classic
        // Sentinel Hub put OAuth at {baseUrl}/oauth/token; CDSE puts it at the URL
        // below. Override the default if you're on classic Sentinel Hub.
        @Value("${bipros.satellite.sentinel-hub.token-url:"
            + "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token}")
        String tokenUrl,
        @Value("${bipros.satellite.sentinel-hub.client-id}") String clientId,
        @Value("${bipros.satellite.sentinel-hub.client-secret}") String clientSecret
    ) {
        this.json = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
        this.tokenUrl = tokenUrl;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.tokenHttp = RestClient.builder().build();
        this.geoJsonWriter.setEncodeCRS(false);
    }

    @Override public String vendorId() { return VENDOR_ID; }

    @Override public String rasterContentType() { return "image/png"; }

    @Override
    public List<SceneDescriptor> findImagery(Polygon aoi, LocalDate from, LocalDate to) {
        String token = getToken();

        ObjectNode body = json.createObjectNode();
        body.put("collections", "[\"sentinel-2-l2a\"]"); // overwritten below as an array
        body.putArray("collections").add("sentinel-2-l2a");
        body.put("datetime", from + "T00:00:00Z/" + to + "T23:59:59Z");
        try {
            body.set("intersects", json.readTree(geoJsonWriter.write(aoi)));
        } catch (Exception e) {
            throw new BusinessRuleException("SATELLITE_AOI", "AOI serialisation failed: " + e.getMessage());
        }
        body.put("limit", 50);

        JsonNode response = http.post()
            .uri("/api/v1/catalog/1.0.0/search")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) return List.of();
        JsonNode features = response.path("features");
        List<SceneDescriptor> scenes = new ArrayList<>(features.size());
        for (JsonNode feature : features) {
            String sceneId = feature.path("id").asText(null);
            String dateStr = feature.path("properties").path("datetime").asText(null);
            if (sceneId == null || dateStr == null) continue;
            LocalDate capture = Instant.parse(dateStr).atZone(java.time.ZoneOffset.UTC).toLocalDate();
            double cloud = feature.path("properties").path("eo:cloud_cover").asDouble(Double.NaN);
            scenes.add(new SceneDescriptor(
                sceneId, VENDOR_ID, capture,
                Double.isNaN(cloud) ? null : cloud,
                aoi, // STAC feature footprint available but we already have the AOI
                null));
        }
        return scenes;
    }

    @Override
    public byte[] fetchRaster(String sceneId, Polygon aoi, int maxWidthPx) {
        String token = getToken();
        Envelope env = aoi.getEnvelopeInternal();

        ObjectNode request = json.createObjectNode();

        // input
        ObjectNode input = request.putObject("input");
        ObjectNode bounds = input.putObject("bounds");
        ArrayNode bbox = bounds.putArray("bbox");
        bbox.add(env.getMinX()).add(env.getMinY()).add(env.getMaxX()).add(env.getMaxY());
        bounds.putObject("properties").put("crs", "http://www.opengis.net/def/crs/EPSG/0/4326");
        ArrayNode data = input.putArray("data");
        ObjectNode s2 = data.addObject();
        s2.put("type", "sentinel-2-l2a");
        ObjectNode dataFilter = s2.putObject("dataFilter");
        // We accept any scene that matches the catalog id filter pattern.
        dataFilter.put("tileId", sceneId);

        // output
        ObjectNode output = request.putObject("output");
        output.put("width", Math.min(Math.max(maxWidthPx, 256), 2500));
        ObjectNode outHeight = output; // width-height ratio preserved by aspect
        double ratio = (env.getMaxY() - env.getMinY()) / (env.getMaxX() - env.getMinX());
        outHeight.put("height", Math.max(128, (int) Math.round(Math.min(maxWidthPx, 2500) * ratio)));
        ArrayNode responses = output.putArray("responses");
        ObjectNode respDef = responses.addObject();
        respDef.put("identifier", "default");
        // Request PNG (not TIFF) so the downstream vision LLM — Claude,
        // OpenAI — can consume the raster directly. We're not doing geo-
        // processing on the pixels, only visual analysis, so PNG is lossless
        // enough and avoids a server-side TIFF→PNG step. Override via the
        // rasterContentType() method if an analyzer does need GeoTIFF.
        respDef.putObject("format").put("type", "image/png");

        // evalscript
        request.put("evalscript", EVAL_SCRIPT);

        byte[] bytes = http.post()
            .uri("/api/v1/process")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header(HttpHeaders.ACCEPT, "image/png")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("SATELLITE_EMPTY", "Sentinel Hub returned empty raster for scene " + sceneId);
        }
        return bytes;
    }

    /** OAuth2 client-credentials flow; caches the token in memory with a 5-min safety margin. */
    synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minus(Duration.ofMinutes(5)))) {
            return cachedToken;
        }
        log.debug("[SentinelHub] refreshing OAuth2 token via {}", tokenUrl);
        // URL-encoded form body — works for both CDSE (Keycloak) and legacy
        // Sentinel Hub OAuth2 endpoints.
        String form = "grant_type=client_credentials"
            + "&client_id=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8)
            + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode resp = tokenHttp.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
        if (resp == null || !resp.has("access_token")) {
            throw new BusinessRuleException("SATELLITE_AUTH", "Sentinel Hub did not return a token");
        }
        cachedToken = resp.get("access_token").asText();
        int expiresIn = resp.path("expires_in").asInt(3600);
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }
}
