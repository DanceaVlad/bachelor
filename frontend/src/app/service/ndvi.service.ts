import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class NdviService {
    constructor(private http: HttpClient) {}

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

    /**
     * Fetches GeoJSON data from the microservice.
     * @param boundingBox Array of 4 numbers [minLon, minLat, maxLon, maxLat].
     * @returns Observable of the GeoJSON FeatureCollection.
     */
    getNdviData(boundingBox: number[]): Observable<any> {
        // Construct the query parameter string
        const bboxQuery = boundingBox.join(',');
        // Call the microservice endpoint
        console.log(`http://localhost:8080/ndvi?bbox=${bboxQuery}`);
        return this.http.get<any>(`http://localhost:8080/ndvi?bbox=${bboxQuery}`);
    }
}
