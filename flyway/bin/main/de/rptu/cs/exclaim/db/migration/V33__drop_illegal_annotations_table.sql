-- see migration V17
-- The mismatch could happen due to different encoding in the filename string.
-- We now apply a more relaxed strategy only comparing exercise, sheet, team and upload date.
UPDATE illegal_annotations AS ia
SET fileid = (
    SELECT u.id
    FROM uploads AS u
    WHERE u.exercise = ia.exercise
      AND u.sheet = ia.sheet
      AND CONCAT(u.groupid, '|', u.teamid) = ia.team
      AND TO_CHAR(u.upload_date, 'YYYYMMDDHH24MISS') = SUBSTRING(ia.filename FROM 1 FOR 14)
);

INSERT INTO annotations (fileid, line, annotationobj)
SELECT                   fileid, line, annotationobj
FROM illegal_annotations;

DROP TABLE illegal_annotations;
