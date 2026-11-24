-- The shop speaks Vietnamese only: the saved language columns are no longer used. Safe to run more than once.
ALTER TABLE user_preferences DROP COLUMN IF EXISTS language;
ALTER TABLE orders DROP COLUMN IF EXISTS customer_language;
