CREATE TABLE studentres
(sheet VARCHAR(50),
 exercise VARCHAR(50),
 team VARCHAR(200),
 studentid VARCHAR(50),
  PRIMARY KEY (sheet, exercise, studentid),
  FOREIGN KEY (sheet, exercise) REFERENCES sheets(id, exercise))