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
import ImageLayer from 'ol/layer/Image';
import ImageStatic from 'ol/source/ImageStatic';
import JSZip from 'jszip';
import proj4 from 'proj4';
import { register } from 'ol/proj/proj4';
import GeoTIFF from 'ol/source/GeoTIFF';
import WebGLTileLayer from 'ol/layer/WebGLTile';

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
        // Define the custom Sinusoidal projection based on metadata
        proj4.defs(
            "CUSTOM_SIN",
            "+proj=sinu +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs"
        );
        register(proj4);

        effect(() => {
            if (this.showNdviLayer()) {
                this.fetchAndDisplayNdviData();
            } else {
                this.deleteNdviLayer();
            }
        });
    }

    private loadLocalGeoTiff(): void {
        const imgurl = 'https://openlayers.org/data/raster/no-overviews.tif';

        // Define the projection based on metadata
        proj4.defs(
            "EPSG:32618",
            "+proj=utm +zone=18 +datum=WGS84 +units=m +no_defs"
        );
        register(proj4);

        // Update map view to match the GeoTIFF projection and center
        const view = new View({
            projection: "EPSG:32618",
            center: [254880, 2945100], // Center based on metadata
            zoom: 8, // Adjust zoom level as needed
        });

        this.map.setView(view);

        // Load and display the GeoTIFF
        (async () => {
            let dataurl = null;
            if (false) {
                console.log("Fetching image data");
                const response = await fetch(imgurl);
                const blob = await response.blob();
                console.log("Received blob of length ", blob.size);
                dataurl = URL.createObjectURL(blob);
            } else {
                dataurl = imgurl;
            }

            const tiffSource = new GeoTIFF({
                sources: [
                    {
                        url: dataurl,
                        min: 0, // Adjust min value for visualization
                        max: 255, // Adjust max value for visualization
                        nodata: 0, // Handle NoData values
                    },
                ],
            });

            const layer = new WebGLTileLayer({
                source: tiffSource,
            });

            layer.set('name', 'Local GeoTIFF Layer');
            this.map.getLayers().push(layer);

            // Update the view to fit the extent of the GeoTIFF
            tiffSource.getView()
                .then((viewOptions) => {
                    this.map.setView(new View(viewOptions));
                })
                .catch((error) => {
                    console.error('Error setting view from GeoTIFF source:', error);
                });
        })();
    }

    private fetchAndDisplayNdviData(): void {

        const view = new View({
            projection: "CUSTOM_SIN",
            center: this.map.getView().getCenter(), // Center based on current view
            zoom: this.map.getView().getZoom(), // Zoom based on current view
        });

        this.map.setView(view);

        this.ndviService.getNdviFiles(this.boundingBox()).subscribe(async (zipBlob) => {
            const zip = new JSZip();
            const zipContent = await zip.loadAsync(zipBlob);

            // Iterate over all files in the ZIP archive
            for (const fileName of Object.keys(zipContent.files)) {
                if (fileName.endsWith('.tif')) {
                    try {
                        const fileBlob = await zipContent.files[fileName].async('blob');
                        const tiffUrl = URL.createObjectURL(fileBlob);

                        const geoTiffSource = new GeoTIFF({
                            sources: [
                                {
                                    url: tiffUrl,
                                    min: -2000, // Set min value based on NDVI valid range
                                    max: 10000, // Set max value based on NDVI valid range
                                    nodata: -3000, // Handle NoData value
                                },
                            ],
                        });

                        const tiffLayer = new WebGLTileLayer({
                            source: geoTiffSource,
                        });

                        // Use the file name to identify the layer
                        tiffLayer.set('name', `NDVI Layer: ${fileName}`);
                        this.map.addLayer(tiffLayer);

                    } catch (error) {
                        console.error(`Error processing file ${fileName}:`, error);
                    }
                }
            }
        });
    }

    private fetchAndDisplayNdviResponse(): void {
        this.ndviService.getNdviResponse(this.boundingBox()).subscribe(
            (data: any) => {
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
        );
    }

    private deleteNdviLayer(): void {
        this.map.getLayers().forEach(layer => {
            if (layer.get('name') === 'NDVI Layer') {
                this.map.removeLayer(layer);
            }
        });
    }

    ngOnInit(): void {
        // Initialize base map
        const baseLayer = new TileLayer({
            source: new OSM(),
        });
        baseLayer.set('name', 'Base Layer');

        const londonCenterInSin = proj4("EPSG:4326", "CUSTOM_SIN", [-0.12755, 51.507222]);

        const view = new View({
            center: londonCenterInSin,
            zoom: 5,
            projection: "CUSTOM_SIN",
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

            // Transform to EPSG:4326 for display and API usage
            const transformedExtent = transformExtent(extentAbsolute, 'EPSG:3857', 'EPSG:4326');

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
