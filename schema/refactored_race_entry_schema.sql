-- Refactored implementation-oriented schema with stronger, more portable types.
--
-- Portability notes:
-- - Uses broadly portable SQL types and constraint syntax so it can be moved to
--   Microsoft SQL Server with minimal translation.
-- - Avoids SQLite-specific PRAGMAs and weakly-typed-only design choices.
-- - Keeps race classification times as text because the source data mixes elapsed
--   times, gaps, and statuses (for example, '+1 Lap').
-- - Stores qualifying, lap, fastest-lap, and pit duration values in integer
--   milliseconds because those are true durations and are easier to compare.

CREATE TABLE circuits (
    circuit_id INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    CONSTRAINT PK001 PRIMARY KEY (circuit_id)
);

CREATE TABLE drivers (
    driver_ref VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    nationality VARCHAR(80),
    CONSTRAINT PK002 PRIMARY KEY (driver_ref)
);

CREATE TABLE teams (
    team_ref VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    nationality VARCHAR(80),
    CONSTRAINT PK003 PRIMARY KEY (team_ref)
);

CREATE TABLE status (
    status_id INTEGER NOT NULL,
    description VARCHAR(100) NOT NULL,
    CONSTRAINT PK004 PRIMARY KEY (status_id)
);

CREATE TABLE race_weekend (
    year SMALLINT NOT NULL,
    round SMALLINT NOT NULL,
    circuit_id INTEGER NOT NULL,
    prix_date DATE NOT NULL,
    prix_time TIME,
    fp1_date DATE,
    fp1_time TIME,
    qual_date DATE,
    qual_time TIME,
    CONSTRAINT PK005 PRIMARY KEY (year, round),
    CONSTRAINT FK001 FOREIGN KEY (circuit_id)
        REFERENCES circuits(circuit_id),
    CONSTRAINT CK001 CHECK (year >= 1950),
    CONSTRAINT CK002 CHECK (round > 0)
);

CREATE TABLE sprint_weekend (
    year SMALLINT NOT NULL,
    round SMALLINT NOT NULL,
    sprint_date DATE NOT NULL,
    sprint_time TIME,
    CONSTRAINT PK006 PRIMARY KEY (year, round),
    CONSTRAINT FK002 FOREIGN KEY (year, round)
        REFERENCES race_weekend(year, round),
    CONSTRAINT CK003 CHECK (year >= 1950),
    CONSTRAINT CK004 CHECK (round > 0)
);

CREATE TABLE regular_weekend (
    year SMALLINT NOT NULL,
    round SMALLINT NOT NULL,
    fp3_date DATE NOT NULL,
    fp3_time TIME,
    CONSTRAINT PK007 PRIMARY KEY (year, round),
    CONSTRAINT FK003 FOREIGN KEY (year, round)
        REFERENCES race_weekend(year, round),
    CONSTRAINT CK005 CHECK (year >= 1950),
    CONSTRAINT CK006 CHECK (round > 0)
);

CREATE TABLE race_entry (
    entry_id INTEGER NOT NULL,
    year SMALLINT NOT NULL,
    round SMALLINT NOT NULL,
    driver_ref VARCHAR(64) NOT NULL,
    team_ref VARCHAR(64) NOT NULL,
    car_number SMALLINT NOT NULL,
    CONSTRAINT PK008 PRIMARY KEY (entry_id),
    CONSTRAINT FK004 FOREIGN KEY (year, round)
        REFERENCES race_weekend(year, round),
    CONSTRAINT FK005 FOREIGN KEY (driver_ref)
        REFERENCES drivers(driver_ref),
    CONSTRAINT FK006 FOREIGN KEY (team_ref)
        REFERENCES teams(team_ref),
    CONSTRAINT CK007 CHECK (year >= 1950),
    CONSTRAINT CK008 CHECK (round > 0),
    CONSTRAINT CK009 CHECK (car_number >= 0)
);

CREATE TABLE qual_result (
    entry_id INTEGER NOT NULL,
    q1_ms INTEGER,
    q2_ms INTEGER,
    q3_ms INTEGER,
    CONSTRAINT PK009 PRIMARY KEY (entry_id),
    CONSTRAINT FK007 FOREIGN KEY (entry_id)
        REFERENCES race_entry(entry_id)
        ON DELETE CASCADE,
    CONSTRAINT CK010 CHECK (q1_ms IS NULL OR q1_ms > 0),
    CONSTRAINT CK011 CHECK (q2_ms IS NULL OR q2_ms > 0),
    CONSTRAINT CK012 CHECK (q3_ms IS NULL OR q3_ms > 0)
);

CREATE TABLE sprint_perf (
    entry_id INTEGER NOT NULL,
    race_time VARCHAR(32),
    num_laps SMALLINT,
    fastest_lap_time_ms INTEGER,
    fastest_lap_num SMALLINT,
    status_id INTEGER NOT NULL,
    CONSTRAINT PK010 PRIMARY KEY (entry_id),
    CONSTRAINT FK008 FOREIGN KEY (entry_id)
        REFERENCES race_entry(entry_id)
        ON DELETE CASCADE,
    CONSTRAINT FK009 FOREIGN KEY (status_id)
        REFERENCES status(status_id),
    CONSTRAINT CK013 CHECK (num_laps IS NULL OR num_laps >= 0),
    CONSTRAINT CK014 CHECK (fastest_lap_num IS NULL OR fastest_lap_num > 0),
    CONSTRAINT CK015 CHECK (fastest_lap_time_ms IS NULL OR fastest_lap_time_ms > 0)
);

CREATE TABLE prix_perf (
    entry_id INTEGER NOT NULL,
    race_time VARCHAR(32),
    num_laps SMALLINT,
    fastest_lap_time_ms INTEGER,
    fastest_lap_num SMALLINT,
    status_id INTEGER NOT NULL,
    CONSTRAINT PK011 PRIMARY KEY (entry_id),
    CONSTRAINT FK010 FOREIGN KEY (entry_id)
        REFERENCES race_entry(entry_id)
        ON DELETE CASCADE,
    CONSTRAINT FK011 FOREIGN KEY (status_id)
        REFERENCES status(status_id),
    CONSTRAINT CK016 CHECK (num_laps IS NULL OR num_laps >= 0),
    CONSTRAINT CK017 CHECK (fastest_lap_num IS NULL OR fastest_lap_num > 0),
    CONSTRAINT CK018 CHECK (fastest_lap_time_ms IS NULL OR fastest_lap_time_ms > 0)
);

CREATE TABLE lap_info (
    entry_id INTEGER NOT NULL,
    lap_num SMALLINT NOT NULL,
    position SMALLINT,
    lap_time_ms INTEGER,
    CONSTRAINT PK012 PRIMARY KEY (entry_id, lap_num),
    CONSTRAINT FK012 FOREIGN KEY (entry_id)
        REFERENCES race_entry(entry_id)
        ON DELETE CASCADE,
    CONSTRAINT CK019 CHECK (lap_num > 0),
    CONSTRAINT CK020 CHECK (position IS NULL OR position > 0),
    CONSTRAINT CK021 CHECK (lap_time_ms IS NULL OR lap_time_ms > 0)
);

CREATE TABLE pit_stop (
    entry_id INTEGER NOT NULL,
    stop_no SMALLINT NOT NULL,
    lap_num SMALLINT NOT NULL,
    stop_time TIME,
    duration_ms INTEGER,
    CONSTRAINT PK013 PRIMARY KEY (entry_id, stop_no),
    CONSTRAINT FK013 FOREIGN KEY (entry_id)
        REFERENCES race_entry(entry_id)
        ON DELETE CASCADE,
    CONSTRAINT FK014 FOREIGN KEY (entry_id, lap_num)
        REFERENCES lap_info(entry_id, lap_num),
    CONSTRAINT CK022 CHECK (stop_no > 0),
    CONSTRAINT CK023 CHECK (lap_num > 0),
    CONSTRAINT CK024 CHECK (duration_ms IS NULL OR duration_ms >= 0)
);
