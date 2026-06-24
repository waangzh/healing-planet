package com.example.demos.web.pojo.entity;

import lombok.Data;

@Data
public class Items {

    private IrrigationPumpStatus IrrigationPumpStatus;
    private LightLux LightLux;
    private Temperature Temperature;
    private SoilMoisture SoilMoisture;
    private CO2Value CO2Value;
    private EnvironmentHumidity EnvironmentHumidity;
    private PotNumber PotNumber;

}