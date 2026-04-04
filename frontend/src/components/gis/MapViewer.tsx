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

interface MapViewerProps {
  geoJsonData: GeoJsonFeatureCollection;
}

export function MapViewer({ geoJsonData }: MapViewerProps) {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<Map | null>(null);
  const [selectedPolygon, setSelectedPolygon] = useState<any>(null);

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
      "absolute bg-white rounded-lg shadow-lg p-4 z-50";
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
      let clickedFeature: any = null;

      map.forEachFeatureAtPixel(event.pixel, (feature) => {
        clickedFeature = feature;
        return true;
      });

      if (clickedFeature) {
        const props = clickedFeature.getProperties();
        setSelectedPolygon(props);

        const coords = clickedFeature.getGeometry()?.getExtent();
        if (coords) {
          const center = [
            (coords[0] + coords[2]) / 2,
            (coords[1] + coords[3]) / 2,
          ];
          popup.setPosition(center as any);

          popupContainer.innerHTML = `
            <div class="text-sm">
              <h3 class="font-bold text-gray-900">${props.wbsCode}</h3>
              <p class="text-gray-700">${props.wbsName}</p>
            </div>
          `;
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
        className="w-full h-96 bg-gray-100 rounded-lg border border-gray-300"
      />
      {selectedPolygon && (
        <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded">
          <p className="text-sm font-medium text-gray-900">
            Selected: {selectedPolygon.wbsCode}
          </p>
          <p className="text-sm text-gray-600">{selectedPolygon.wbsName}</p>
        </div>
      )}
    </div>
  );
}
