-- The shop speaks Vietnamese only: every saved language choice becomes Vietnamese. Safe to run more than once.
UPDATE user_preferences SET language = 'vi' WHERE language <> 'vi';
UPDATE orders SET customer_language = 'vi' WHERE customer_language IS NOT NULL AND customer_language <> 'vi';
