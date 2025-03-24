/*
- Use userid instead of studentid
*/

-- Rename existing constraints such that we can re-use those names
ALTER TABLE unread RENAME CONSTRAINT pk__unread TO old_pk__unread;
ALTER TABLE unread RENAME CONSTRAINT fk__unread__uploads TO old_fk__unread__uploads;

CREATE TABLE unread2
(
    fileid INT NOT NULL,
    userid INT NOT NULL,

    CONSTRAINT pk__unread PRIMARY KEY (fileid, userid),
    CONSTRAINT fk__unread__uploads FOREIGN KEY (fileid) REFERENCES uploads(id),
    CONSTRAINT fk__unread__users FOREIGN KEY (userid) REFERENCES users(userid)
);

-- Move data
INSERT INTO unread2 (fileid,       userid)
SELECT        unread.fileid, users.userid
FROM unread
LEFT JOIN users ON users.studentid = unread.studentid;

DROP TABLE unread;
ALTER TABLE unread2 RENAME TO unread;
