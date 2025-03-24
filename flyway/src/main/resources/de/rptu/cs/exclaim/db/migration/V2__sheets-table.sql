CREATE TABLE sheets
(id VARCHAR(50),
 exercise VARCHAR(50),
 label VARCHAR(200),
 PRIMARY KEY (id, exercise));

ALTER TABLE sheets ADD FOREIGN KEY (exercise) REFERENCES exercises(id);
