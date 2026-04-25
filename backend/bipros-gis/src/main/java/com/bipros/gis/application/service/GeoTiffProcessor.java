package com.bipros.gis.application.service;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.locationtech.jts.geom.Envelope;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Reads a GeoTIFF byte array, extracts its WGS84 bounding envelope, renders it to
 * a PNG, and optionally scales the result so it doesn't exceed a reasonable display
 * size for the OpenLayers {@code ImageStatic} source and the AI vision analyzer.
 */
@Component
@Slf4j
public class GeoTiffProcessor {

    private static final int MAX_DIMENSION = 2048;

    public ProcessedImage process(byte[] bytes) {
        GeoTiffFormat format = new GeoTiffFormat();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            if (!format.accepts(bais)) {
                throw new BusinessRuleException("INVALID_GEOTIFF", "File is not a valid GeoTIFF");
            }
        } catch (IOException e) {
            throw new BusinessRuleException("INVALID_GEOTIFF", "Cannot read file: " + e.getMessage());
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            GridCoverage2DReader reader = format.getReader(bais);
            GridCoverage2D coverage = reader.read(null);

            CoordinateReferenceSystem sourceCrs = coverage.getCoordinateReferenceSystem2D();
            Envelope envelope = new Envelope(
                coverage.getEnvelope2D().getMinX(),
                coverage.getEnvelope2D().getMaxX(),
                coverage.getEnvelope2D().getMinY(),
                coverage.getEnvelope2D().getMaxY()
            );

            CoordinateReferenceSystem wgs84 = DefaultGeographicCRS.WGS84;
            if (!CRS.equalsIgnoreMetadata(sourceCrs, wgs84)) {
                MathTransform transform = CRS.findMathTransform(sourceCrs, wgs84, true);
                envelope = JTS.transform(envelope, transform);
            }

            BufferedImage png = renderToPng(coverage);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(png, "png", baos);

            return new ProcessedImage(
                baos.toByteArray(),
                envelope.getMinY(),
                envelope.getMaxY(),
                envelope.getMinX(),
                envelope.getMaxX()
            );
        } catch (Exception e) {
            log.warn("GeoTIFF processing failed: {}", e.getMessage());
            throw new BusinessRuleException("GEOTIFF_PARSE_FAILED",
                "Failed to process GeoTIFF: " + e.getMessage());
        }
    }

    private BufferedImage renderToPng(GridCoverage2D coverage) {
        java.awt.image.RenderedImage renderedImage = coverage.getRenderedImage();
        int width = renderedImage.getWidth();
        int height = renderedImage.getHeight();

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bufferedImage.createGraphics();
        g.drawRenderedImage(renderedImage, new AffineTransform());
        g.dispose();

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);
            Image scaled = bufferedImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaledImage.createGraphics();
            g2.drawImage(scaled, 0, 0, null);
            g2.dispose();
            return scaledImage;
        }

        return bufferedImage;
    }

    public record ProcessedImage(byte[] pngBytes, double southBound, double northBound,
                                  double westBound, double eastBound) {}
}
