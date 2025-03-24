CREATE TABLE study_statistics
(
    userid             INT           NOT NULL,
    course_of_studies  VARCHAR(255)  NOT NULL,
    semester           VARCHAR(255)  NOT NULL,

    CONSTRAINT pk__study_statistics PRIMARY KEY (userid),
    CONSTRAINT fk__study_statistics__users FOREIGN KEY (userid) REFERENCES users(userid)
);
