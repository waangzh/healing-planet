package com.example.demos.web.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlantStateServiceImplTest {
    @Test
    void shouldClassifyTrendWithFivePercentTolerance() {
        assertEquals("STABLE", PlantStateServiceImpl.trend(100d, 104d));
        assertEquals("INCREASING", PlantStateServiceImpl.trend(100d, 106d));
        assertEquals("DECREASING", PlantStateServiceImpl.trend(100d, 90d));
        assertEquals("UNKNOWN", PlantStateServiceImpl.trend(null, 90d));
    }
}
