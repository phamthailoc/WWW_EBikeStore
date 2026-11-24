-- V4: new accounts follow the device's light/dark setting ('system') by default.
-- Until now nothing in the app read the theme, so every existing row only holds the old column default;
-- reset those so signed-in customers are not forced into the light theme. Safe to run more than once.
-- Apply with: mysql -u root -p ebike_db < update_user_preferences_defaults.sql

ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'system';
UPDATE user_preferences SET theme = 'system' WHERE theme = 'light';
