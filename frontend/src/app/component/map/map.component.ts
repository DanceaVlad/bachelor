import { Component, OnInit, signal, effect } from '@angular/core';
import GeoJSON from 'ol/format/GeoJSON';
import TileLayer from 'ol/layer/Tile';
import Map from 'ol/Map';
import { fromLonLat } from 'ol/proj';
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
    styleUrl: './map.component.scss',
})
export class MapComponent implements OnInit {
    map!: Map;

    showNvdaLayer = signal(false);
    nvdaData: any;

    constructor(private readonly nvdaService: NvdaService) {
        effect(() => {

            if(this.showNvdaLayer()) {
                // Fetch NVDA data and add it to the map
                this.nvdaService.getGeoJsonData().subscribe(data => {
                    this.nvdaData = data;
                    const vectorLayer = new VectorLayer({
                        source: new VectorSource({
                            features: new GeoJSON().readFeatures(data, {
                                dataProjection: 'EPSG:4326',
                                featureProjection: 'EPSG:3857',
                            }),
                        }),
                    });
                    vectorLayer.set('name', 'NVDA Layer');
                    this.map.addLayer(vectorLayer);
                });
            } else {
                // Remove NVDA layer from the map
                this.map.getLayers().forEach(layer => {
                    if (layer.get('name') === 'NVDA Layer') {
                        this.map.removeLayer(layer);
                    }
                });
            }
        });
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
            controls: defaultControls({attribution: false}),
        });
    }

    onToggleNvda(): void {
        this.showNvdaLayer.update(value => !value);
    }
}
