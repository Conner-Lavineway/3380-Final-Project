-- Extracted from `stage1/Comp 3380 Winter 2026 - Stage 1 Submission.pdf`
-- on 2026-03-30.
--
-- This is the Stage 1 proposed schema written out as a normal SQL file so it is
-- easier to read, review, diff, and evolve than keeping it buried in the PDF.
--
-- Notes:
-- - This preserves the Appendix A schema from the proposal as closely as possible.
-- - `QUAL_RESULT.position` is intentionally omitted because the proposal marks it
--   as derivable.
-- - The relational-model section mentions `CONTRACT(driverRef, year, round,
--   teamRef, car_number)`, but Appendix A does not include a `CREATE TABLE`
--   statement for it, so it is not invented here.

CREATE TABLE IF NOT EXISTS CIRCUITS (
    circuitID INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    city TEXT NOT NULL,
    country TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS DRIVERS (
    driverRef TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    nationality TEXT
);

CREATE TABLE IF NOT EXISTS TEAMS (
    teamRef TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    nationality TEXT
);

CREATE TABLE IF NOT EXISTS STATUS (
    statusID INTEGER PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS RACE_WEEKEND (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    circuitID INTEGER NOT NULL,
    prix_date TEXT NOT NULL,
    prix_time TEXT,
    fp1_date TEXT,
    fp1_time TEXT,
    qual_date TEXT,
    qual_time TEXT,
    PRIMARY KEY (year, round),
    FOREIGN KEY (circuitID) REFERENCES CIRCUITS(circuitID)
);

CREATE TABLE IF NOT EXISTS SPRINT_WEEKEND (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    sprint_date TEXT NOT NULL,
    sprint_time TEXT,
    PRIMARY KEY (year, round),
    FOREIGN KEY (year, round) REFERENCES RACE_WEEKEND(year, round)
);

CREATE TABLE IF NOT EXISTS REGULAR_WEEKEND (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    fp3_date TEXT NOT NULL,
    fp3_time TEXT,
    PRIMARY KEY (year, round),
    FOREIGN KEY (year, round) REFERENCES RACE_WEEKEND(year, round)
);

CREATE TABLE IF NOT EXISTS QUAL_RESULT (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    driverRef TEXT NOT NULL,
    q1 TEXT,
    q2 TEXT,
    q3 TEXT,
    PRIMARY KEY (year, round, driverRef),
    FOREIGN KEY (year, round) REFERENCES RACE_WEEKEND(year, round),
    FOREIGN KEY (driverRef) REFERENCES DRIVERS(driverRef)
);

CREATE TABLE IF NOT EXISTS SPRINT_PERF (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    driverRef TEXT NOT NULL,
    racetime TEXT,
    lapNum INTEGER,
    fLapTime TEXT,
    fLapNum INTEGER,
    statusID INTEGER NOT NULL,
    PRIMARY KEY (year, round, driverRef),
    FOREIGN KEY (year, round) REFERENCES SPRINT_WEEKEND(year, round),
    FOREIGN KEY (driverRef) REFERENCES DRIVERS(driverRef),
    FOREIGN KEY (statusID) REFERENCES STATUS(statusID)
);

CREATE TABLE IF NOT EXISTS PRIX_PERF (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    driverRef TEXT NOT NULL,
    racetime TEXT,
    lapNum INTEGER,
    fLapTime TEXT,
    fLapNum INTEGER,
    statusID INTEGER NOT NULL,
    PRIMARY KEY (year, round, driverRef),
    FOREIGN KEY (year, round) REFERENCES RACE_WEEKEND(year, round),
    FOREIGN KEY (driverRef) REFERENCES DRIVERS(driverRef),
    FOREIGN KEY (statusID) REFERENCES STATUS(statusID)
);

CREATE TABLE IF NOT EXISTS LAP_INFO (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    driverRef TEXT NOT NULL,
    lapNum INTEGER NOT NULL,
    position INTEGER,
    lap_time TEXT,
    PRIMARY KEY (year, round, driverRef, lapNum),
    FOREIGN KEY (year, round) REFERENCES RACE_WEEKEND(year, round),
    FOREIGN KEY (driverRef) REFERENCES DRIVERS(driverRef)
);

CREATE TABLE IF NOT EXISTS PIT_STOP (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    driverRef TEXT NOT NULL,
    lapNum INTEGER NOT NULL,
    time TEXT,
    duration REAL,
    PRIMARY KEY (year, round, driverRef, lapNum),
    FOREIGN KEY (year, round, driverRef, lapNum)
        REFERENCES LAP_INFO(year, round, driverRef, lapNum)
);
