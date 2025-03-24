CREATE TABLE deltapoints
(sheet VARCHAR(50),
 exercise VARCHAR(50),
 studentid VARCHAR(50),
 delta DECIMAL(20,1),
 reason VARCHAR,
  PRIMARY KEY (sheet, exercise, studentid),
  FOREIGN KEY (sheet, exercise) REFERENCES sheets(id, exercise))