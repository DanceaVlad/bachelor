import { Component, OnInit } from '@angular/core';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import XYZ from 'ol/source/XYZ';
import { fromLonLat } from 'ol/proj';
import MousePosition from 'ol/control/MousePosition';
import * as control from 'ol/control';
import LayerGroup from 'ol/layer/Group';
import LayerSwitcher from 'ol-layerswitcher';
import 'ol-layerswitcher/dist/ol-layerswitcher.css';

@Component({
  selector: 'app-map',
  standalone: true,
  imports: [],
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
})
export class MapComponent implements OnInit {
  map!: Map;

  constructor() {}

  ngOnInit(): void {
    this.initializeMap();
  }

  initializeMap() {
    const mousePositionControl = new MousePosition({
      className: 'custom-mouse-position',
      target: document.getElementById('mouse-position') || undefined,
    });

    const baseLayer = new TileLayer({
      source: new XYZ({
        url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      }),
    });
    baseLayer.set('title', 'OpenStreetMap'); // Dynamically set title for LayerSwitcher
    baseLayer.set('type', 'base'); // Mark as base layer

    const ndviLayer = new TileLayer({
      opacity: 1.0,
      source: new XYZ({
        url: 'http://localhost:8080/tiles/{z}/{x}/{-y}.png',
        tileSize: 256,
      }),
    });
    ndviLayer.set('title', 'NDVI Overlay'); // Dynamically set title for LayerSwitcher

    const baseLayers = new LayerGroup({
      layers: [baseLayer],
    });

    const overlayLayers = new LayerGroup({
      layers: [ndviLayer],
    });
    overlayLayers.set('title', 'Overlays'); // Dynamically set title for LayerSwitcher

    this.map = new Map({
      controls: control.defaults().extend([mousePositionControl]),
      target: 'map',
      layers: [baseLayers, overlayLayers],
      view: new View({
        center: fromLonLat([0, 0]),
        zoom: 2,
      }),
    });

    const layerSwitcher = new LayerSwitcher({
      activationMode: 'click',
      groupSelectStyle: 'group',
      startActive: true,
    });

    this.map.addControl(layerSwitcher);
  }
}
