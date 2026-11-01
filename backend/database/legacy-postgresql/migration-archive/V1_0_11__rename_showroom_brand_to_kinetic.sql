UPDATE ebike_order.showrooms
SET name = REPLACE(name, 'YADEA', 'KINETIC')
WHERE name LIKE 'YADEA%';
