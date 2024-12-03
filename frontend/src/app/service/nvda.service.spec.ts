import { TestBed } from '@angular/core/testing';

import { NvdaService } from './nvda.service';

describe('NvdaService', () => {
    let service: NvdaService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(NvdaService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
