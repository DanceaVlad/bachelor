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

    const baseLayers = new LayerGroup({
      layers: [
        new TileLayer({
          visible: true,
          source: new XYZ({
            url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          }),
        }),
      ],
    });

    const overlayLayer = new LayerGroup({
      layers: [
        new TileLayer({
          opacity: 1.0,
          extent: [-20037508.341676, -8399252.552216, 20037211.940435, 149088738.179522],
          source: new XYZ({
            url: 'http://localhost:8080/tiles/{z}/{x}/{-y}.png',
            tileSize: 256,
          }),
        }),
      ],
    });

    this.map = new Map({
      controls: control.defaults().extend([mousePositionControl]),
      target: 'map',
      layers: [baseLayers, overlayLayer],
      view: new View({
        center: fromLonLat([0, 0]),
        zoom: 0,
      }),
    });

    const layerSwitcher = new LayerSwitcher();
    this.map.addControl(layerSwitcher);
  }
}
