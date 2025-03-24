CREATE TABLE unread
(
 fileid int NOT NULL,
 studentid VARCHAR(50),
 PRIMARY KEY (fileid, studentid),
 FOREIGN KEY (fileid) REFERENCES uploads(id));
