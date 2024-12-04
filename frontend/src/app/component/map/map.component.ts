import { Component, OnInit, signal, effect } from '@angular/core';
import GeoJSON from 'ol/format/GeoJSON';
import TileLayer from 'ol/layer/Tile';
import Map from 'ol/Map';
import { fromLonLat, transformExtent } from 'ol/proj';
import OSM from 'ol/source/OSM';
import View from 'ol/View';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { NvdaService } from '../../service/nvda.service';
import { defaults as defaultControls } from 'ol/control';

@Component({
    selector: 'app-map',
    standalone: true,
    imports: [],
    templateUrl: './map.component.html',
    styleUrls: ['./map.component.scss'],
})
export class MapComponent implements OnInit {
    map!: Map;

    showNvdaLayer = signal(false);
    boundingBox = signal([0, 0, 0, 0]);
    viewableCoordinates = signal([0, 0, 0, 0]);

    constructor(private readonly nvdaService: NvdaService) {

        // React to NVDA layer toggling but not to the bounding box
        effect(() => {

            if (this.showNvdaLayer()) {
                // Fetch and display NDVI data
                this.nvdaService.getNvdiData(this.boundingBox()).subscribe(
                    (data: any) => {
                        this.addNdviLayer(data);
                    }
                );
            } else {
                // Remove NDVI layer from the map
                this.map.getLayers().forEach(layer => {
                    if (layer.get('name') === 'NDVI Layer') {
                        this.map.removeLayer(layer);
                    }
                });
            }
            this.map.getLayers().forEach(layer => {
                console.log('Layer:', layer.get('name'));
            });
        });

    }

    private addNdviLayer(data: any): void {
        const vectorLayer = new VectorLayer({
            source: new VectorSource({
                features: new GeoJSON().readFeatures(data, {
                    dataProjection: 'EPSG:4326',
                    featureProjection: 'EPSG:3857',
                }),
            }),
        });
        vectorLayer.set('name', 'NDVI Layer');
        this.map.addLayer(vectorLayer);
    }

    ngOnInit(): void {
        // Initialize base map
        const baseLayer = new TileLayer({
            source: new OSM(),
        });
        baseLayer.set('name', 'Base Layer');

        const view = new View({
            center: fromLonLat([-0.12755, 51.507222]),
            zoom: 13,
        });
        view.set('name', 'London');

        this.map = new Map({
            target: 'map',
            layers: [baseLayer],
            view: view,
            controls: defaultControls({ attribution: false }),
        });

        // Update signals when the map view changes
        const updateBoundingBox = () => {
            const extentAbsolute = this.map.getView().calculateExtent();
            const transformedExtent = transformExtent(extentAbsolute, 'EPSG:3857', 'EPSG:4326');
            const [minLon, minLat, maxLon, maxLat] = transformedExtent.map(coord => Number(coord.toFixed(2)));

            this.boundingBox.set(transformedExtent); // Updates the boundingBox signal
            this.viewableCoordinates.set([minLon, minLat, maxLon, maxLat]); // Updates the viewableCoordinates signal
        };

        // Update bounding box on moveend and pointerdrag
        this.map.on('moveend', updateBoundingBox);
        this.map.on('pointerdrag', updateBoundingBox);
    }

    onToggleNvda(): void {
        this.showNvdaLayer.update(value => !value);
        console.log('showNvdaLayer changed:', this.showNvdaLayer());
    }
}
