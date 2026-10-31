package com.ebike.productModule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "product_specifications")
public class ProductSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "model_code", length = 100)
    private String modelCode;

    @Column(nullable = false, length = 100)
    private String brand = "Yadea-style";

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private VehicleType vehicleType = VehicleType.E_SCOOTER;

    @Enumerated(EnumType.STRING)
    @Column(name = "battery_type", length = 50)
    private BatteryType batteryType;

    @Column(name = "battery_capacity_ah", precision = 8, scale = 2)
    private BigDecimal batteryCapacityAh;

    @Column(name = "battery_voltage_v", precision = 8, scale = 2)
    private BigDecimal batteryVoltageV;

    @Column(name = "charging_time_hours", precision = 5, scale = 2)
    private BigDecimal chargingTimeHours;

    @Column(name = "max_speed_kmh", precision = 6, scale = 2)
    private BigDecimal maxSpeedKmh;

    @Column(name = "max_range_km", precision = 6, scale = 2)
    private BigDecimal maxRangeKm;

    @Column(name = "motor_power_watts")
    private Integer motorPowerWatts;

    @Column(name = "peak_motor_power_watts")
    private Integer peakMotorPowerWatts;

    @Column(name = "max_load_kg", precision = 8, scale = 2)
    private BigDecimal maxLoadKg;

    @Column(name = "product_weight_kg", precision = 8, scale = 2)
    private BigDecimal productWeightKg;

    @Column(name = "wheel_size_inch", precision = 4, scale = 2)
    private BigDecimal wheelSizeInch;

    @Enumerated(EnumType.STRING)
    @Column(name = "brake_type", length = 50)
    private BrakeType brakeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type", length = 50)
    private DriveType driveType;

    @Column(name = "water_resistance_rating", length = 20)
    private String waterResistanceRating;

    @Column(name = "frame_material", length = 100)
    private String frameMaterial;

    @Column(name = "suspension_type", length = 100)
    private String suspensionType;

    @Column(name = "display_type", length = 100)
    private String displayType;

    @Column(name = "smart_features", columnDefinition = "TEXT")
    private String smartFeatures;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "dimensions_mm", length = 100)
    private String dimensionsMm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public BatteryType getBatteryType() { return batteryType; }
    public void setBatteryType(BatteryType batteryType) { this.batteryType = batteryType; }
    public BigDecimal getBatteryCapacityAh() { return batteryCapacityAh; }
    public void setBatteryCapacityAh(BigDecimal batteryCapacityAh) { this.batteryCapacityAh = batteryCapacityAh; }
    public BigDecimal getBatteryVoltageV() { return batteryVoltageV; }
    public void setBatteryVoltageV(BigDecimal batteryVoltageV) { this.batteryVoltageV = batteryVoltageV; }
    public BigDecimal getChargingTimeHours() { return chargingTimeHours; }
    public void setChargingTimeHours(BigDecimal chargingTimeHours) { this.chargingTimeHours = chargingTimeHours; }
    public BigDecimal getMaxSpeedKmh() { return maxSpeedKmh; }
    public void setMaxSpeedKmh(BigDecimal maxSpeedKmh) { this.maxSpeedKmh = maxSpeedKmh; }
    public BigDecimal getMaxRangeKm() { return maxRangeKm; }
    public void setMaxRangeKm(BigDecimal maxRangeKm) { this.maxRangeKm = maxRangeKm; }
    public Integer getMotorPowerWatts() { return motorPowerWatts; }
    public void setMotorPowerWatts(Integer motorPowerWatts) { this.motorPowerWatts = motorPowerWatts; }
    public Integer getPeakMotorPowerWatts() { return peakMotorPowerWatts; }
    public void setPeakMotorPowerWatts(Integer peakMotorPowerWatts) { this.peakMotorPowerWatts = peakMotorPowerWatts; }
    public BigDecimal getMaxLoadKg() { return maxLoadKg; }
    public void setMaxLoadKg(BigDecimal maxLoadKg) { this.maxLoadKg = maxLoadKg; }
    public BigDecimal getProductWeightKg() { return productWeightKg; }
    public void setProductWeightKg(BigDecimal productWeightKg) { this.productWeightKg = productWeightKg; }
    public BigDecimal getWheelSizeInch() { return wheelSizeInch; }
    public void setWheelSizeInch(BigDecimal wheelSizeInch) { this.wheelSizeInch = wheelSizeInch; }
    public BrakeType getBrakeType() { return brakeType; }
    public void setBrakeType(BrakeType brakeType) { this.brakeType = brakeType; }
    public DriveType getDriveType() { return driveType; }
    public void setDriveType(DriveType driveType) { this.driveType = driveType; }
    public String getWaterResistanceRating() { return waterResistanceRating; }
    public void setWaterResistanceRating(String waterResistanceRating) { this.waterResistanceRating = waterResistanceRating; }
    public String getFrameMaterial() { return frameMaterial; }
    public void setFrameMaterial(String frameMaterial) { this.frameMaterial = frameMaterial; }
    public String getSuspensionType() { return suspensionType; }
    public void setSuspensionType(String suspensionType) { this.suspensionType = suspensionType; }
    public String getDisplayType() { return displayType; }
    public void setDisplayType(String displayType) { this.displayType = displayType; }
    public String getSmartFeatures() { return smartFeatures; }
    public void setSmartFeatures(String smartFeatures) { this.smartFeatures = smartFeatures; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public String getDimensionsMm() { return dimensionsMm; }
    public void setDimensionsMm(String dimensionsMm) { this.dimensionsMm = dimensionsMm; }
}
