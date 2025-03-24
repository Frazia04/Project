CREATE TABLE assignments
(id VARCHAR(50),
 exercise VARCHAR(50),
 sheet VARCHAR(50),
 label VARCHAR(200),
 maxpoints DECIMAL(20,1),
 PRIMARY KEY (id, exercise, sheet));

ALTER TABLE assignments ADD FOREIGN KEY (sheet, exercise) REFERENCES sheets(id, exercise);
