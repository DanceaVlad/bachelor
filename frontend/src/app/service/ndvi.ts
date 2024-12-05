import { TestBed } from '@angular/core/testing';

import { NdviService } from './ndvi.service';

describe('NdviService', () => {
    let service: NdviService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(NdviService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
