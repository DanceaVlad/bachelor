import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class NdviService {
    constructor(private http: HttpClient) {}

    /**
     * Sends a request to the backend to trigger the local download of the NDVI data.
     */
    initializeData(): Observable<any> {
        return this.http.get<any>(`http://localhost:8080/initialize-data`);
    }
}
