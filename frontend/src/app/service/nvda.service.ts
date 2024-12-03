import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class NvdaService {
    constructor() {}
    getGeoJsonData(): Observable<GeoJSON.FeatureCollection> {
        return of({
            type: 'FeatureCollection',
            features: [
                {
                    type: 'Feature',
                    geometry: {
                        type: 'Point',
                        coordinates: [-0.1276, 51.5074], // Coordinates for London Center (Longitude, Latitude)
                    },
                    properties: {
                        name: 'London Center',
                    },
                },
                {
                    type: 'Feature',
                    geometry: {
                        type: 'Polygon',
                        coordinates: [
                            [
                                [-0.15, 51.505], // Bottom-left
                                [-0.1, 51.505], // Bottom-right
                                [-0.1, 51.51], // Top-right
                                [-0.15, 51.51], // Top-left
                                [-0.15, 51.505], // Close polygon
                            ],
                        ],
                    },
                    properties: {
                        name: 'Example Area',
                    },
                },
            ],
        });
    }

    getNvdaData(boundingBox: number[]): Observable<any> {
        return of({
            type: 'FeatureCollection',
            features: [
                {
                    type: 'Feature',
                    geometry: {
                        type: 'Point',
                        coordinates: [(boundingBox[0] + boundingBox[2])/2 , (boundingBox[1] + boundingBox[3])/2], // Coordinates for London Center (Longitude, Latitude)
                    },
                    properties: {
                        name: 'London Center',
                    },
                },
                {
                    type: 'Feature',
                    geometry: {
                        type: 'Polygon',
                        coordinates: [
                            [
                                [-0.15, 51.505], // Bottom-left
                                [-0.1, 51.505], // Bottom-right
                                [-0.1, 51.51], // Top-right
                                [-0.15, 51.51], // Top-left
                                [-0.15, 51.505], // Close polygon
                            ],
                        ],
                    },
                    properties: {
                        name: 'Example Area',
                    },
                },
            ],
        });
    }
}
