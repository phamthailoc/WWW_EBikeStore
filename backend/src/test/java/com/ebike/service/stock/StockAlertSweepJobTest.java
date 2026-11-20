package com.ebike.service.stock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

/** V22: one colour failing must not stop the sweep for the others. */
class StockAlertSweepJobTest {

    @Test void aFailureOnOneColourDoesNotStopTheRest() {
        StockAlertService service = mock(StockAlertService.class);
        when(service.waitingVariantIdsInStock()).thenReturn(List.of(1L, 2L, 3L));
        when(service.notifyRestockedById(2L)).thenThrow(new IllegalStateException("boom"));
        new StockAlertSweepJob(service).sweep();
        verify(service).notifyRestockedById(1L);
        verify(service).notifyRestockedById(2L);
        verify(service).notifyRestockedById(3L);
    }

    @Test void aFailureToListTheColoursEndsTheRunQuietly() {
        StockAlertService service = mock(StockAlertService.class);
        when(service.waitingVariantIdsInStock()).thenThrow(new IllegalStateException("db down"));
        new StockAlertSweepJob(service).sweep();
    }
}
