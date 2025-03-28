/*
The current schema of storing multiple group ids and multiple friends in a single column is not normalized.
Create separate tables for group and team preferences, in a normalized fashion.
*/

CREATE TYPE t_group_preference AS ENUM ('PREFERRED', 'POSSIBLE', 'DISLIKE', 'IMPOSSIBLE');

-- Use ON DELETE CASCADE such that students can unregister even if they already entered preferences data.
-- The preferences will be deleted implicitly when unregistering.

CREATE TABLE grouppreferences
(
    userid     INT                                                    NOT NULL,
    exerciseid VARCHAR(50)                                            NOT NULL,
    groupid    VARCHAR(50)                                            NOT NULL,
    preference t_group_preference                                     NOT NULL,

    CONSTRAINT pk__grouppreferences PRIMARY KEY (userid, exerciseid, groupid),
    CONSTRAINT fk__grouppreferences__students FOREIGN KEY (userid, exerciseid) REFERENCES students(userid, exerciseid) ON DELETE CASCADE,
    CONSTRAINT fk__grouppreferences__groups FOREIGN KEY (exerciseid, groupid) REFERENCES groups(exerciseid, groupid)
);

CREATE TABLE teampreferences
(
    userid        INT          NOT NULL,
    exerciseid    VARCHAR(50)  NOT NULL,
    friend_userid INT          NOT NULL,

    CONSTRAINT pk__teampreferences PRIMARY KEY (userid, exerciseid, friend_userid),
    CONSTRAINT fk__teampreferences_user__students FOREIGN KEY (userid, exerciseid) REFERENCES students(userid, exerciseid) ON DELETE CASCADE,
    CONSTRAINT fk__teampreferences_friend__students FOREIGN KEY (friend_userid, exerciseid) REFERENCES students(userid, exerciseid) ON DELETE CASCADE,
    CONSTRAINT check__teampreferences__not_own_friend CHECK (userid <> friend_userid)
);


-- Delete preferences for students that are not registered in the exercise
DELETE FROM preferences AS p
WHERE NOT EXISTS (
    SELECT 1
    FROM students AS s
    WHERE s.userid = p.userid AND s.exerciseid = p.exerciseid
);

-- Move group preferences, but only for groups that still exist
INSERT INTO grouppreferences (userid,   exerciseid,   groupid, preference)
SELECT                      p.userid, p.exerciseid, g.groupid, 'PREFERRED'::t_group_preference
FROM preferences AS p
INNER JOIN groups AS g ON g.exerciseid = p.exerciseid AND POSITION(CONCAT(',', g.groupid, ',') IN CONCAT(',', p.preferred, ',')) <> 0
UNION
SELECT                      p.userid, p.exerciseid, g.groupid, 'POSSIBLE'::t_group_preference
FROM preferences AS p
INNER JOIN groups AS g ON g.exerciseid = p.exerciseid AND POSITION(CONCAT(',', g.groupid, ',') IN CONCAT(',', p.possible, ',')) <> 0
UNION
SELECT                      p.userid, p.exerciseid, g.groupid, 'DISLIKE'::t_group_preference
FROM preferences AS p
INNER JOIN groups AS g ON g.exerciseid = p.exerciseid AND POSITION(CONCAT(',', g.groupid, ',') IN CONCAT(',', p.dislike, ',')) <> 0
UNION
SELECT                      p.userid, p.exerciseid, g.groupid, 'IMPOSSIBLE'::t_group_preference
FROM preferences AS p
INNER JOIN groups AS g ON g.exerciseid = p.exerciseid AND POSITION(CONCAT(',', g.groupid, ',') IN CONCAT(',', p.impossible, ',')) <> 0;


-- Move team preferences, but only if the friend is still registered for the exercise
INSERT INTO teampreferences (userid,   exerciseid, friend_userid)
SELECT                     p.userid, p.exerciseid, f.userid
FROM preferences AS p
INNER JOIN users f ON POSITION(CONCAT(',', f.username, ',') IN CONCAT(',', p.friends, ',')) <> 0 AND f.userid <> p.userid
INNER JOIN students s ON s.userid = f.userid AND s.exerciseid = p.exerciseid;


DROP TABLE preferences;
