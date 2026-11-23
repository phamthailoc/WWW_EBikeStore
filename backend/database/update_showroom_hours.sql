-- Opening and closing time of a showroom as two columns, so test-ride slots no longer depend on how the free text "opening_hours" is written.
-- When both are set they decide the slots; the text stays for display and for days off ("CN nghỉ"). Safe to run more than once.
ALTER TABLE showrooms ADD COLUMN IF NOT EXISTS open_time TIME NULL AFTER opening_hours;
ALTER TABLE showrooms ADD COLUMN IF NOT EXISTS close_time TIME NULL AFTER open_time;
-- the showrooms of the sample data open 08:00 - 21:00
UPDATE showrooms SET open_time = '08:00:00', close_time = '21:00:00'
WHERE open_time IS NULL AND close_time IS NULL AND opening_hours = '08:00 - 21:00';
