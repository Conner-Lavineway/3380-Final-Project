#!/usr/bin/env python3

from __future__ import annotations

import argparse
import sqlite3
import sys
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


NULL_SENTINELS = {None, "", "\\N"}


def clean_text(value: Any) -> str | None:
    if value in NULL_SENTINELS:
        return None
    return str(value)


def clean_int(value: Any) -> int | None:
    text = clean_text(value)
    if text is None:
        return None
    return int(text)


def clean_float(value: Any) -> float | None:
    text = clean_text(value)
    if text is None:
        return None
    return float(text)


def parse_duration_ms(value: Any) -> int | None:
    text = clean_text(value)
    if text is None:
        return None

    total_seconds = 0.0
    for component in text.split(":"):
        total_seconds = (total_seconds * 60) + float(component)
    return int(round(total_seconds * 1000))


def full_name(forename: Any, surname: Any) -> str:
    parts = [part for part in (clean_text(forename), clean_text(surname)) if part]
    return " ".join(parts)


def connect_source(path: Path) -> sqlite3.Connection:
    uri = f"file:{path.as_posix()}?mode=ro"
    connection = sqlite3.connect(uri, uri=True)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA temp_store = MEMORY")
    return connection


def connect_target(path: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


def result_preference_key(row: sqlite3.Row) -> tuple[int, int, int, float, int]:
    laps = clean_int(row["laps"])
    position_order = clean_int(row["positionOrder"])
    points = clean_float(row["points"])
    result_id = clean_int(row["resultId"])
    return (
        0 if laps is not None else 1,
        -(laps or -1),
        position_order if position_order is not None else 10**9,
        -(points or -1.0),
        result_id if result_id is not None else 10**9,
    )


@dataclass(frozen=True)
class EntrySeed:
    source_kind: str
    source_id: int
    race_id: int
    driver_id: int
    team_id: int
    number: int | None
    source_name: str


@dataclass(frozen=True)
class MultiResultGroup:
    race_id: int
    driver_id: int
    result_ids: tuple[int, ...]
    reason: str


def load_row_map(connection: sqlite3.Connection, query: str, key_columns: tuple[str, ...]) -> dict[tuple[Any, ...], sqlite3.Row]:
    rows = connection.execute(query).fetchall()
    return {tuple(row[column] for column in key_columns): row for row in rows}


def load_all_results(connection: sqlite3.Connection) -> list[sqlite3.Row]:
    return connection.execute(
        """
        SELECT resultId, raceId, driverId, teamId, number, laps, positionOrder,
               points, time, fastestLap, fastestLapTime, statusId
        FROM results
        ORDER BY raceId, driverId, resultId
        """
    ).fetchall()


def analyze_result_rows(
    result_rows: list[sqlite3.Row],
) -> tuple[
    dict[tuple[int, int], list[sqlite3.Row]],
    list[MultiResultGroup],
    int,
]:
    grouped_rows: dict[tuple[int, int], list[sqlite3.Row]] = defaultdict(list)
    for row in result_rows:
        grouped_rows[(row["raceId"], row["driverId"])].append(row)

    multi_result_groups: list[MultiResultGroup] = []
    extra_result_rows = 0

    for key, rows in grouped_rows.items():
        if len(rows) > 1:
            extra_result_rows += len(rows) - 1
            result_ids = tuple(
                result_id
                for result_id in (clean_int(row["resultId"]) for row in rows)
                if result_id is not None
            )
            multi_result_groups.append(
                MultiResultGroup(
                    race_id=key[0],
                    driver_id=key[1],
                    result_ids=result_ids,
                    reason="historical multi-entry preserved",
                )
            )

    return grouped_rows, multi_result_groups, extra_result_rows


def write_migration_log(
    log_path: Path,
    source_db: Path,
    target_db: Path,
    counts: dict[str, int],
    multi_result_groups: list[MultiResultGroup],
    race_id_to_weekend: dict[int, tuple[int, int]],
    driver_id_to_ref: dict[int, str],
) -> None:
    lines = [
        "F1 migration report",
        f"generated_at: {datetime.now().isoformat(timespec='seconds')}",
        f"source_db: {source_db}",
        f"target_db: {target_db}",
        "",
        "summary:",
    ]

    for key in [
        "circuits",
        "drivers",
        "teams",
        "status",
        "race_weekend",
        "sprint_weekend",
        "regular_weekend",
        "race_entry",
        "qual_result",
        "sprint_perf",
        "prix_perf",
        "lap_info",
        "pit_stop",
        "entries_from_qualifying",
        "entries_from_sprint_results",
        "entries_from_results",
        "preserved_multi_result_groups",
        "preserved_multi_result_extra_rows",
    ]:
        lines.append(f"  {key}: {counts[key]}")

    lines.extend([
        "",
        "dropped_result_rows: 0",
        f"preserved_multi_result_groups: {len(multi_result_groups)}",
        f"preserved_multi_result_extra_rows: {counts['preserved_multi_result_extra_rows']}",
    ])

    if multi_result_groups:
        lines.append("")
        lines.append("preserved_multi_result_details:")
        for group in multi_result_groups:
            year, round_number = race_id_to_weekend[group.race_id]
            driver_ref = driver_id_to_ref[group.driver_id]
            result_ids = ", ".join(str(result_id) for result_id in group.result_ids)
            lines.append(
                "  "
                f"year={year} round={round_number} race_id={group.race_id} "
                f"driver_ref={driver_ref} result_ids=[{result_ids}] reason=\"{group.reason}\""
            )

    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text("\n".join(lines) + "\n")


def build_entry_seeds(
    qualifying: dict[tuple[int, int], sqlite3.Row],
    sprint_results: dict[tuple[int, int], sqlite3.Row],
    result_rows: list[sqlite3.Row],
) -> tuple[list[EntrySeed], dict[str, int], dict[tuple[int, int], tuple[str, int]]]:
    seeds: list[EntrySeed] = []
    source_counts = {"qualifying": 0, "sprint_results": 0, "results": 0}
    primary_seed_keys: dict[tuple[int, int], tuple[str, int]] = {}

    result_driver_keys: set[tuple[int, int]] = set()
    result_seed_counts: dict[tuple[int, int], int] = defaultdict(int)

    for row in result_rows:
        result_id = clean_int(row["resultId"])
        if result_id is None:
            raise ValueError("Encountered result row without resultId")

        key = (row["raceId"], row["driverId"])
        result_driver_keys.add(key)
        result_seed_counts[key] += 1
        seeds.append(
            EntrySeed(
                source_kind="results",
                source_id=result_id,
                race_id=row["raceId"],
                driver_id=row["driverId"],
                team_id=row["teamId"],
                number=clean_int(row["number"]),
                source_name="results",
            )
        )
        source_counts["results"] += 1

    for row in result_rows:
        key = (row["raceId"], row["driverId"])
        if result_seed_counts[key] == 1:
            result_id = clean_int(row["resultId"])
            if result_id is None:
                raise ValueError("Encountered result row without resultId")
            primary_seed_keys[key] = ("results", result_id)

    for key, row in qualifying.items():
        if key in result_driver_keys:
            continue

        qualify_id = clean_int(row["qualifyId"])
        if qualify_id is None:
            raise ValueError("Encountered qualifying row without qualifyId")

        seeds.append(
            EntrySeed(
                source_kind="qualifying",
                source_id=qualify_id,
                race_id=row["raceId"],
                driver_id=row["driverId"],
                team_id=row["teamId"],
                number=clean_int(row["number"]),
                source_name="qualifying",
            )
        )
        source_counts["qualifying"] += 1
        primary_seed_keys[key] = ("qualifying", qualify_id)

    for key, row in sprint_results.items():
        if key in primary_seed_keys:
            continue

        sprint_result_id = clean_int(row["sprintResultId"])
        if sprint_result_id is None:
            raise ValueError("Encountered sprint result row without sprintResultId")

        seeds.append(
            EntrySeed(
                source_kind="sprint_results",
                source_id=sprint_result_id,
                race_id=row["raceId"],
                driver_id=row["driverId"],
                team_id=row["teamId"],
                number=clean_int(row["number"]),
                source_name="sprint_results",
            )
        )
        source_counts["sprint_results"] += 1
        primary_seed_keys[key] = ("sprint_results", sprint_result_id)

    return seeds, source_counts, primary_seed_keys


def insert_lookup_tables(source: sqlite3.Connection, target: sqlite3.Connection) -> dict[str, int]:
    circuits_rows = [
        (
            row["circuitId"],
            row["name"],
            row["city"],
            row["country"],
        )
        for row in source.execute("SELECT circuitId, name, city, country FROM circuits ORDER BY circuitId")
    ]
    target.executemany(
        "INSERT INTO circuits (circuit_id, name, city, country) VALUES (?, ?, ?, ?)",
        circuits_rows,
    )

    driver_rows = [
        (
            row["driverRef"],
            full_name(row["forename"], row["surname"]),
            clean_text(row["nationality"]),
        )
        for row in source.execute(
            "SELECT driverRef, forename, surname, nationality FROM drivers ORDER BY driverId"
        )
    ]
    target.executemany(
        "INSERT INTO drivers (driver_ref, name, nationality) VALUES (?, ?, ?)",
        driver_rows,
    )

    team_rows = [
        (
            row["teamRef"],
            row["name"],
            clean_text(row["nationality"]),
        )
        for row in source.execute("SELECT teamRef, name, nationality FROM teams ORDER BY teamId")
    ]
    target.executemany(
        "INSERT INTO teams (team_ref, name, nationality) VALUES (?, ?, ?)",
        team_rows,
    )

    status_rows = [
        (row["statusId"], row["status"])
        for row in source.execute("SELECT statusId, status FROM status ORDER BY statusId")
    ]
    target.executemany(
        "INSERT INTO status (status_id, description) VALUES (?, ?)",
        status_rows,
    )

    return {
        "circuits": len(circuits_rows),
        "drivers": len(driver_rows),
        "teams": len(team_rows),
        "status": len(status_rows),
    }


def insert_weekends(source: sqlite3.Connection, target: sqlite3.Connection) -> dict[str, int]:
    race_weekend_rows = [
        (
            row["year"],
            row["round"],
            row["circuitId"],
            clean_text(row["date"]),
            clean_text(row["time"]),
            clean_text(row["fp1_date"]),
            clean_text(row["fp1_time"]),
            clean_text(row["quali_date"]),
            clean_text(row["quali_time"]),
        )
        for row in source.execute(
            """
            SELECT year, round, circuitId, date, time, fp1_date, fp1_time, quali_date, quali_time
            FROM races
            ORDER BY year, round
            """
        )
    ]
    target.executemany(
        """
        INSERT INTO race_weekend (
            year, round, circuit_id, prix_date, prix_time, fp1_date, fp1_time, qual_date, qual_time
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        race_weekend_rows,
    )

    sprint_weekend_rows = [
        (
            row["year"],
            row["round"],
            clean_text(row["sprint_date"]),
            clean_text(row["sprint_time"]),
        )
        for row in source.execute(
            "SELECT year, round, sprint_date, sprint_time FROM races WHERE sprint_date IS NOT NULL AND sprint_date != '' AND sprint_date != '\\N' ORDER BY year, round"
        )
    ]
    target.executemany(
        "INSERT INTO sprint_weekend (year, round, sprint_date, sprint_time) VALUES (?, ?, ?, ?)",
        sprint_weekend_rows,
    )

    regular_weekend_rows = [
        (
            row["year"],
            row["round"],
            clean_text(row["fp3_date"]),
            clean_text(row["fp3_time"]),
        )
        for row in source.execute(
            "SELECT year, round, fp3_date, fp3_time FROM races WHERE fp3_date IS NOT NULL AND fp3_date != '' AND fp3_date != '\\N' ORDER BY year, round"
        )
    ]
    target.executemany(
        "INSERT INTO regular_weekend (year, round, fp3_date, fp3_time) VALUES (?, ?, ?, ?)",
        regular_weekend_rows,
    )

    return {
        "race_weekend": len(race_weekend_rows),
        "sprint_weekend": len(sprint_weekend_rows),
        "regular_weekend": len(regular_weekend_rows),
    }


def migrate(
    source_path: Path,
    target_path: Path,
    schema_path: Path,
    log_path: Path,
) -> dict[str, int]:
    source = connect_source(source_path)
    target = connect_target(target_path)

    try:
        with target:
            target.executescript(schema_path.read_text())

            insert_counts = {}
            insert_counts.update(insert_lookup_tables(source, target))
            insert_counts.update(insert_weekends(source, target))

            driver_id_to_ref = {
                row["driverId"]: row["driverRef"]
                for row in source.execute("SELECT driverId, driverRef FROM drivers")
            }
            team_id_to_ref = {
                row["teamId"]: row["teamRef"]
                for row in source.execute("SELECT teamId, teamRef FROM teams")
            }
            race_id_to_weekend = {
                row["raceId"]: (row["year"], row["round"])
                for row in source.execute("SELECT raceId, year, round FROM races")
            }

            qualifying = load_row_map(
                source,
                "SELECT qualifyId, raceId, driverId, teamId, number, q1, q2, q3 FROM qualifying",
                ("raceId", "driverId"),
            )
            sprint_results = load_row_map(
                source,
                "SELECT sprintResultId, raceId, driverId, teamId, number, laps, time, fastestLap, fastestLapTime, statusId FROM sprint_results",
                ("raceId", "driverId"),
            )
            result_rows = load_all_results(source)
            _, multi_result_groups, extra_result_rows = analyze_result_rows(result_rows)

            entry_seeds, entry_source_counts, primary_seed_keys = build_entry_seeds(
                qualifying,
                sprint_results,
                result_rows,
            )

            race_entry_rows = []
            entry_id_map: dict[tuple[str, int], int] = {}
            next_entry_id = 1
            for seed in sorted(
                entry_seeds,
                key=lambda seed: (
                    race_id_to_weekend[seed.race_id][0],
                    race_id_to_weekend[seed.race_id][1],
                    driver_id_to_ref[seed.driver_id],
                    seed.source_name,
                    seed.source_id,
                ),
            ):
                year, round_number = race_id_to_weekend[seed.race_id]
                race_entry_rows.append(
                    (
                        next_entry_id,
                        year,
                        round_number,
                        driver_id_to_ref[seed.driver_id],
                        team_id_to_ref[seed.team_id],
                        seed.number if seed.number is not None else 0,
                    )
                )
                entry_id_map[(seed.source_kind, seed.source_id)] = next_entry_id
                next_entry_id += 1

            primary_entry_id_by_driver_race = {
                key: entry_id_map[source_key] for key, source_key in primary_seed_keys.items()
            }

            target.executemany(
                """
                INSERT INTO race_entry (entry_id, year, round, driver_ref, team_ref, car_number)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                race_entry_rows,
            )

            qual_rows = [
                (
                    primary_entry_id_by_driver_race[key],
                    parse_duration_ms(row["q1"]),
                    parse_duration_ms(row["q2"]),
                    parse_duration_ms(row["q3"]),
                )
                for key, row in qualifying.items()
                if key in primary_entry_id_by_driver_race
            ]
            target.executemany(
                "INSERT INTO qual_result (entry_id, q1_ms, q2_ms, q3_ms) VALUES (?, ?, ?, ?)",
                qual_rows,
            )

            sprint_rows = [
                (
                    primary_entry_id_by_driver_race[key],
                    clean_text(row["time"]),
                    clean_int(row["laps"]),
                    parse_duration_ms(row["fastestLapTime"]),
                    clean_int(row["fastestLap"]),
                    clean_int(row["statusId"]),
                )
                for key, row in sprint_results.items()
                if key in primary_entry_id_by_driver_race
            ]
            target.executemany(
                """
                INSERT INTO sprint_perf (
                    entry_id, race_time, num_laps, fastest_lap_time_ms,
                    fastest_lap_num, status_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                sprint_rows,
            )

            prix_rows = [
                (
                    entry_id_map[("results", clean_int(row["resultId"]))],
                    clean_text(row["time"]),
                    clean_int(row["laps"]),
                    parse_duration_ms(row["fastestLapTime"]),
                    clean_int(row["fastestLap"]),
                    clean_int(row["statusId"]),
                )
                for row in result_rows
            ]
            target.executemany(
                """
                INSERT INTO prix_perf (
                    entry_id, race_time, num_laps, fastest_lap_time_ms,
                    fastest_lap_num, status_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                prix_rows,
            )

            lap_rows = [
                (
                    primary_entry_id_by_driver_race[(row["raceId"], row["driverId"])],
                    clean_int(row["lap"]),
                    clean_int(row["position"]),
                    parse_duration_ms(row["time"]),
                )
                for row in source.execute(
                    "SELECT raceId, driverId, lap, position, time FROM lap_times ORDER BY raceId, driverId, lap"
                )
            ]
            target.executemany(
                "INSERT INTO lap_info (entry_id, lap_num, position, lap_time_ms) VALUES (?, ?, ?, ?)",
                lap_rows,
            )

            pit_rows = [
                (
                    primary_entry_id_by_driver_race[(row["raceId"], row["driverId"])],
                    clean_int(row["stop"]),
                    clean_int(row["lap"]),
                    clean_text(row["time"]),
                    parse_duration_ms(row["duration"]),
                )
                for row in source.execute(
                    "SELECT raceId, driverId, stop, lap, time, duration FROM pit_stops ORDER BY raceId, driverId, stop"
                )
            ]
            target.executemany(
                "INSERT INTO pit_stop (entry_id, stop_no, lap_num, stop_time, duration_ms) VALUES (?, ?, ?, ?, ?)",
                pit_rows,
            )

            insert_counts.update(
                {
                    "race_entry": len(race_entry_rows),
                    "qual_result": len(qual_rows),
                    "sprint_perf": len(sprint_rows),
                    "prix_perf": len(prix_rows),
                    "lap_info": len(lap_rows),
                    "pit_stop": len(pit_rows),
                    "entries_from_qualifying": entry_source_counts["qualifying"],
                    "entries_from_sprint_results": entry_source_counts["sprint_results"],
                    "entries_from_results": entry_source_counts["results"],
                    "preserved_multi_result_groups": len(multi_result_groups),
                    "preserved_multi_result_extra_rows": extra_result_rows,
                }
            )

            write_migration_log(
                log_path=log_path,
                source_db=source_path,
                target_db=target_path,
                counts=insert_counts,
                multi_result_groups=multi_result_groups,
                race_id_to_weekend=race_id_to_weekend,
                driver_id_to_ref=driver_id_to_ref,
            )

        return insert_counts
    finally:
        source.close()
        target.close()


def parse_args() -> argparse.Namespace:
    project_root = Path(__file__).resolve().parents[1]
    default_source = project_root / "dbs" / "F1_original.db"
    default_target = project_root / "dbs" / "F1_refactored.db"
    default_log = project_root / "dbs" / "F1_migration.log"
    default_schema = project_root / "schema" / "refactored_race_entry_schema.sql"

    parser = argparse.ArgumentParser(
        description="Migrate the legacy F1 SQLite database into the refactored race-entry schema."
    )
    parser.add_argument("--source-db", type=Path, default=default_source)
    parser.add_argument("--target-db", type=Path, default=default_target)
    parser.add_argument("--log-file", type=Path, default=default_log)
    parser.add_argument("--schema", type=Path, default=default_schema)
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Replace the target database if it already exists.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    source_db = args.source_db.resolve()
    target_db = args.target_db.resolve()
    log_file = args.log_file.resolve()
    schema_path = args.schema.resolve()

    if not source_db.exists():
        print(f"Source database not found: {source_db}", file=sys.stderr)
        return 1

    if not schema_path.exists():
        print(f"Schema file not found: {schema_path}", file=sys.stderr)
        return 1

    if source_db == target_db:
        print("Source and target databases must be different files.", file=sys.stderr)
        return 1

    target_db.parent.mkdir(parents=True, exist_ok=True)
    if target_db.exists():
        if not args.overwrite:
            print(
                f"Target database already exists: {target_db}. Use --overwrite to replace it.",
                file=sys.stderr,
            )
            return 1
        target_db.unlink()

    counts = migrate(source_db, target_db, schema_path, log_file)

    print(f"Migrated '{source_db}' -> '{target_db}'")
    print(f"Wrote migration log to '{log_file}'")
    ordered_keys = [
        "circuits",
        "drivers",
        "teams",
        "status",
        "race_weekend",
        "sprint_weekend",
        "regular_weekend",
        "race_entry",
        "qual_result",
        "sprint_perf",
        "prix_perf",
        "lap_info",
        "pit_stop",
        "entries_from_qualifying",
        "entries_from_sprint_results",
        "entries_from_results",
        "preserved_multi_result_groups",
        "preserved_multi_result_extra_rows",
    ]
    for key in ordered_keys:
        print(f"{key}: {counts[key]}")

    print(
        "Note: historical multi-entry results are kept."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
