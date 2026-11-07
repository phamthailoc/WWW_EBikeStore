-- B8: an electric bicycle is at most 25 km/h and 250 W. A vehicle over either limit is a motorbike in law and must
-- be registered and plated, so it must not be classified as E_BIKE. Data only. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_vehicle_types.sql
UPDATE product_specifications
SET vehicle_type = 'E_MOTORBIKE'
WHERE vehicle_type = 'E_BIKE'
  AND (max_speed_kmh > 25 OR motor_power_watts > 250);
