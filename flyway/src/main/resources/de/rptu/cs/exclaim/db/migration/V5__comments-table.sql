CREATE TABLE comments
(sheet VARCHAR(50),
 exercise VARCHAR(50),
 team VARCHAR(200),
 comment VARCHAR,
 PRIMARY KEY (sheet, exercise, team),
 FOREIGN KEY (sheet, exercise) REFERENCES sheets(id, exercise))