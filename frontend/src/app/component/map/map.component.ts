import { Component, OnInit, signal } from '@angular/core';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import XYZ from 'ol/source/XYZ';
import { fromLonLat } from 'ol/proj';
import { NdviService } from '../../service/ndvi.service';

@Component({
    selector: 'app-map',
    standalone: true,
    imports: [],
    templateUrl: './map.component.html',
    styleUrls: ['./map.component.scss'],
})
export class MapComponent implements OnInit {

    map!: Map;
    boundingBox = signal([0, 0, 0, 0]);
    ndviService: NdviService;

    constructor(ndviService: NdviService) {
        this.ndviService = ndviService
     }

    ngOnInit(): void {
        this.initializeMap();
    }

    initializeMap() {
        // Base Map Layer (OpenStreetMap)
        const baseLayer = new TileLayer({
            source: new XYZ({
                url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', // OSM tiles
            }),
            opacity: 0.1,
        });

        // NDVI Tile Layer (Java Backend)
        const ndviLayer = new TileLayer({
            source: new XYZ({
                url: 'http://localhost:8080/tiles/{z}/{x}/{y}.png', // Replace with your Java backend endpoint
            }),
            opacity: 1.0, // Adjust opacity for transparency over OSM tiles
        });

        // Initialize Map
        this.map = new Map({
            target: 'map', // The ID of the HTML element where the map will render
            layers: [baseLayer, ndviLayer], // Add layers to the map
            view: new View({
                center: fromLonLat([0, 0]), // Center the map on longitude 0, latitude 0
                zoom: 2, // Set the initial zoom level
            }),
        });

        // Update the bounding box whenever the view changes
        this.map.getView().on('change:center', () => this.updateBoundingBox());
        this.map.getView().on('change:resolution', () => this.updateBoundingBox());
      }

    updateBoundingBox() {
        const extent = this.map.getView().calculateExtent();
        const boundingBox = [
            extent[0],
            extent[1],
            extent[2],
            extent[3],
        ];
        this.boundingBox.set(boundingBox); // Update the bounding box signal
    }

    onToggleNdvi() {
        console.log('Toggle NDVI');
        this.ndviService.downloadGeoTiffs();
    }
}
