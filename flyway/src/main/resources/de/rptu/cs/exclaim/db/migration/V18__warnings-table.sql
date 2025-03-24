CREATE TABLE warnings
(
 fileid int NOT NULL,
 line int,
 rule VARCHAR(50),
 ruleset VARCHAR(50),
 infourl VARCHAR,
 priority int,
 message VARCHAR,
 PRIMARY KEY (fileid, line, rule),
 FOREIGN KEY (fileid) REFERENCES uploads(id));
