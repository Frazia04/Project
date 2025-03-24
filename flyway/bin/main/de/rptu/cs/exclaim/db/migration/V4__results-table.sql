CREATE TABLE results
(assignment VARCHAR(50),
 sheet VARCHAR(50),
 exercise VARCHAR(50),
 team VARCHAR(200),
 points DECIMAL(20,1),
 PRIMARY KEY (assignment, sheet, exercise, team),
 FOREIGN KEY (assignment, sheet, exercise) REFERENCES assignments(id, sheet, exercise));
