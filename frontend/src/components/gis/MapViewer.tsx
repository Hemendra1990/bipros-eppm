"use client";

import { useEffect, useRef, useState } from "react";
import { Map, View } from "ol";
import TileLayer from "ol/layer/Tile";
import VectorLayer from "ol/layer/Vector";
import OSM from "ol/source/OSM";
import VectorSource from "ol/source/Vector";
import Feature from "ol/Feature";
import { Style, Fill, Stroke, Text } from "ol/style";
import GeoJSON from "ol/format/GeoJSON";
import "ol/ol.css";
import { GeoJsonFeatureCollection } from "@/lib/api/gisApi";
import Overlay from "ol/Overlay";
import type { FeatureLike } from "ol/Feature";

interface MapViewerProps {
  geoJsonData: GeoJsonFeatureCollection;
}

export function MapViewer({ geoJsonData }: MapViewerProps) {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<Map | null>(null);
  const [selectedPolygon, setSelectedPolygon] = useState<Record<string, unknown> | null>(null);

  useEffect(() => {
    if (!mapRef.current || !geoJsonData) return;

    // Create vector source and layer from GeoJSON
    const vectorSource = new VectorSource({
      features: new GeoJSON().readFeatures(geoJsonData, {
        featureProjection: "EPSG:3857",
      }),
    });

    const vectorLayer = new VectorLayer({
      source: vectorSource,
      style: (feature) => {
        const props = feature.getProperties();
        return new Style({
          fill: new Fill({
            color: props.fillColor || "#3388ff",
          }),
          stroke: new Stroke({
            color: props.strokeColor || "#000000",
            width: 2,
          }),
          text: new Text({
            text: props.wbsCode || "",
            fill: new Fill({ color: "#000" }),
            font: "12px Arial",
          }),
        });
      },
    });

    // Create base map with OSM
    const baseLayer = new TileLayer({
      source: new OSM(),
    });

    // Create popup overlay
    const popupContainer = document.createElement("div");
    popupContainer.id = "popup";
    popupContainer.className =
      "absolute bg-surface rounded-lg shadow-lg p-4 z-50 border border-border";
    document.body.appendChild(popupContainer);

    const popup = new Overlay({
      element: popupContainer,
      autoPan: true,
    });

    // Initialize map
    const map = new Map({
      target: mapRef.current,
      layers: [baseLayer, vectorLayer],
      view: new View({
        center: [8388635.2, 2147483.6], // Default to India center
        zoom: 4,
        projection: "EPSG:3857",
      }),
      overlays: [popup],
    });

    // Handle click on features
    map.on("click", (event) => {
      let clickedFeature: FeatureLike | null = null;

      map.forEachFeatureAtPixel(event.pixel, (feature) => {
        clickedFeature = feature;
        return true;
      });

      if (clickedFeature) {
        const feat = clickedFeature as Feature;
        const props = feat.getProperties();
        setSelectedPolygon(props);

        const coords = feat.getGeometry()?.getExtent();
        if (coords) {
          const center = [
            (coords[0] + coords[2]) / 2,
            (coords[1] + coords[3]) / 2,
          ];
          popup.setPosition(center as any);

          popupContainer.textContent = '';
          const div = document.createElement('div');
          div.className = 'text-sm';
          const h3 = document.createElement('h3');
          h3.className = 'font-bold text-text-primary';
          h3.textContent = props.wbsCode;
          const p = document.createElement('p');
          p.className = 'text-text-secondary';
          p.textContent = props.wbsName;
          div.appendChild(h3);
          div.appendChild(p);
          popupContainer.appendChild(div);
        }
      } else {
        popup.setPosition(undefined);
        setSelectedPolygon(null);
      }
    });

    // Change cursor on hover
    map.on("pointermove", (event) => {
      const isHovering = map.hasFeatureAtPixel(event.pixel);
      mapRef.current!.style.cursor = isHovering ? "pointer" : "default";
    });

    mapInstance.current = map;

    return () => {
      map.dispose();
      popupContainer.remove();
    };
  }, [geoJsonData]);

  return (
    <div className="relative w-full">
      <div
        ref={mapRef}
        className="w-full h-96 bg-surface-hover rounded-lg border border-border"
      />
      {selectedPolygon && (
        <div className="mt-4 p-3 bg-blue-950 border border-blue-700 rounded">
          <p className="text-sm font-medium text-text-primary">
            Selected: {String(selectedPolygon.wbsCode ?? "")}
          </p>
          <p className="text-sm text-text-secondary">{String(selectedPolygon.wbsName ?? "")}</p>
        </div>
      )}
    </div>
  );
}
