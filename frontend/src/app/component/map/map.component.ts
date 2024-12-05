import { Component, OnInit, signal, effect } from '@angular/core';
import GeoJSON from 'ol/format/GeoJSON';
import TileLayer from 'ol/layer/Tile';
import Map from 'ol/Map';
import { fromLonLat, transformExtent } from 'ol/proj';
import OSM from 'ol/source/OSM';
import View from 'ol/View';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import { NdviService } from '../../service/ndvi.service';
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

    showNdviLayer = signal(false);
    boundingBox = signal([0, 0, 0, 0]);
    viewableCoordinates = signal([0, 0, 0, 0]);
    counter = signal(0);

    constructor(private readonly ndviService: NdviService) {
        // React to NDVI layer toggling
        effect(() => {
            if (this.showNdviLayer()) {
                // Fetch and display NDVI data
                this.ndviService.getNdviData(this.boundingBox()).subscribe(
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
        });
    }

    private addNdviLayer(data: any): void {
        const vectorLayer = new VectorLayer({
            source: new VectorSource({
                features: new GeoJSON().readFeatures(data, {
                    dataProjection: 'EPSG:4326', // WGS84 for MODIS data
                    featureProjection: 'EPSG:3857', // Map display projection
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
            center: fromLonLat([-0.12755, 51.507222]), // London coordinates
            zoom: 5,
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
            console.log('Extent in EPSG:3857:', extentAbsolute);

            // Transform to EPSG:4326 for display and API usage
            const transformedExtent = transformExtent(extentAbsolute, 'EPSG:3857', 'EPSG:4326');
            console.log('Transformed extent in EPSG:4326:', transformedExtent);

            if (this.validateExtent(transformedExtent)) {
                this.boundingBox.set(transformedExtent);

                // Truncate for viewableCoordinates display
                this.viewableCoordinates.set(
                    transformedExtent.map(coord => parseFloat(coord.toFixed(2)))
                );
            } else {
                console.error('Invalid bounding box extent:', transformedExtent);
            }
        };

        // Update bounding box on moveend and pointerdrag
        this.map.on('moveend', updateBoundingBox);
        this.map.on('pointerdrag', updateBoundingBox);
    }

    onToggleNdvi(): void {
        this.showNdviLayer.update(value => !value);
    }

    private validateExtent(extent: number[]): boolean {
        return extent.length === 4 &&
            extent[0] >= -180 &&
            extent[1] >= -90 &&
            extent[2] <= 180 &&
            extent[3] <= 90;
    }
}
