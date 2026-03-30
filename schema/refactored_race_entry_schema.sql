-- Refactored implementation-oriented schema.
--
-- Main design change:
-- - The proposal's `CONTRACT(driverRef, year, round, teamRef, car_number)`
--   relation is turned into a single weak-in-concept entry entity named
--   `race_entry`.
-- - `race_entry` gets a surrogate key (`entry_id`) for convenience.
-- - All weekend participation facts (qualifying, sprint result, grand prix
--   result, laps, pit stops) hang off `race_entry` instead of repeatedly using
--   `(year, round, driverRef)`.
--

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS circuits (
    circuit_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    city TEXT NOT NULL,
    country TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS drivers (
    driver_ref TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    nationality TEXT
);

CREATE TABLE IF NOT EXISTS teams (
    team_ref TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    nationality TEXT
);

CREATE TABLE IF NOT EXISTS status (
    status_id INTEGER PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS race_weekend (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    circuit_id INTEGER NOT NULL,
    prix_date TEXT NOT NULL,
    prix_time TEXT,
    fp1_date TEXT,
    fp1_time TEXT,
    qual_date TEXT,
    qual_time TEXT,
    PRIMARY KEY (year, round),
    FOREIGN KEY (circuit_id) REFERENCES circuits(circuit_id)
);

CREATE TABLE IF NOT EXISTS sprint_weekend (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    sprint_date TEXT NOT NULL,
    sprint_time TEXT,
    PRIMARY KEY (year, round),
    FOREIGN KEY (year, round) REFERENCES race_weekend(year, round)
);

CREATE TABLE IF NOT EXISTS regular_weekend (
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    fp3_date TEXT NOT NULL,
    fp3_time TEXT,
    PRIMARY KEY (year, round),
    FOREIGN KEY (year, round) REFERENCES race_weekend(year, round)
);

CREATE TABLE IF NOT EXISTS race_entry (
    entry_id INTEGER PRIMARY KEY,
    year INTEGER NOT NULL,
    round INTEGER NOT NULL,
    driver_ref TEXT NOT NULL,
    team_ref TEXT NOT NULL,
    car_number INTEGER NOT NULL,
    UNIQUE (year, round, driver_ref),
    UNIQUE (year, round, car_number),
    FOREIGN KEY (year, round) REFERENCES race_weekend(year, round),
    FOREIGN KEY (driver_ref) REFERENCES drivers(driver_ref),
    FOREIGN KEY (team_ref) REFERENCES teams(team_ref)
);

CREATE TABLE IF NOT EXISTS qual_result (
    entry_id INTEGER PRIMARY KEY,
    q1 TEXT,
    q2 TEXT,
    q3 TEXT,
    FOREIGN KEY (entry_id) REFERENCES race_entry(entry_id)
);

CREATE TABLE IF NOT EXISTS sprint_perf (
    entry_id INTEGER PRIMARY KEY,
    race_time TEXT,
    num_laps INTEGER,
    fastest_lap_time TEXT,
    fastest_lap_num INTEGER,
    status_id INTEGER NOT NULL,
    FOREIGN KEY (entry_id) REFERENCES race_entry(entry_id),
    FOREIGN KEY (status_id) REFERENCES status(status_id)
);

CREATE TABLE IF NOT EXISTS prix_perf (
    entry_id INTEGER PRIMARY KEY,
    race_time TEXT,
    num_laps INTEGER,
    fastest_lap_time TEXT,
    fastest_lap_num INTEGER,
    status_id INTEGER NOT NULL,
    FOREIGN KEY (entry_id) REFERENCES race_entry(entry_id),
    FOREIGN KEY (status_id) REFERENCES status(status_id)
);

CREATE TABLE IF NOT EXISTS lap_info (
    entry_id INTEGER NOT NULL,
    lap_num INTEGER NOT NULL,
    position INTEGER,
    lap_time TEXT,
    PRIMARY KEY (entry_id, lap_num),
    FOREIGN KEY (entry_id) REFERENCES race_entry(entry_id)
);

CREATE TABLE IF NOT EXISTS pit_stop (
    entry_id INTEGER NOT NULL,
    lap_num INTEGER NOT NULL,
    stop_time TEXT,
    duration REAL,
    PRIMARY KEY (entry_id, lap_num),
    FOREIGN KEY (entry_id) REFERENCES race_entry(entry_id),
    FOREIGN KEY (entry_id, lap_num) REFERENCES lap_info(entry_id, lap_num)
);

