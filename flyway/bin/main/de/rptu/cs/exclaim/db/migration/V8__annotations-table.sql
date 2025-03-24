CREATE TABLE annotations
(exercise VARCHAR(50),
 sheet VARCHAR(50),
 assignment VARCHAR(50),
 team VARCHAR(200),
 filename VARCHAR(256),
 line int,
 annotationObj VARCHAR,
 PRIMARY KEY (exercise, sheet, assignment, team, filename, line),
 FOREIGN KEY (sheet, exercise) REFERENCES sheets(id, exercise),
 FOREIGN KEY (assignment, exercise, sheet) REFERENCES assignments(id,exercise,sheet));
