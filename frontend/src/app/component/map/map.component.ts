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
        baseLayer.set('title', 'OpenStreetMap');
        baseLayer.set('type', 'base'); // Mark as base layer

        const msplanetaryLayer = new TileLayer({
            opacity: 1.0,
            source: new XYZ({
                url: 'http://localhost:8080/msplanetary/tiles/{z}/{x}/{-y}.png',
                tileSize: 256,
            }),
        });
        msplanetaryLayer.set('title', 'MsPlanetary');

        const planetLayer = new TileLayer({
            opacity: 1.0,
            source: new XYZ({
                url: 'https://tiles.planet.com/basemaps/v1/planet-tiles/global_monthly_2016_04_mosaic/gmap/{z}/{x}/{y}.png?api_key=PLAK380f55a7c89f4c4aa9753286349bf874',
                tileSize: 256,
            }),
        });
        planetLayer.set('title', 'Planet.com');

        const baseLayers = new LayerGroup({
            layers: [baseLayer],
        });

        const overlayLayers = new LayerGroup({
            layers: [msplanetaryLayer, planetLayer],
        });
        overlayLayers.set('title', 'Overlays');

        this.map = new Map({
            controls: control.defaults().extend([mousePositionControl]),
            target: 'map',
            layers: [baseLayers, overlayLayers],
            view: new View({
                center: fromLonLat([103.83123, 1.47233]),
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
