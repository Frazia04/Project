CREATE TABLE attendance
(exercise VARCHAR(50),
 sheet VARCHAR(50),
 studentid VARCHAR(50),
 attended BOOLEAN,
 PRIMARY KEY (sheet, exercise, studentid),
 FOREIGN KEY (sheet, exercise) REFERENCES sheets(id, exercise))