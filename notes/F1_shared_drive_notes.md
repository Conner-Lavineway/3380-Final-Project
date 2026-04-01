# F1 multi-entry notes

## Findings

- Old F1 allowed shared / relief drives, so one driver could have multiple classified results in one GP.
- Sources differ on the 1958 change; they agree shared drives stopped earning points.
- The 1960 Argentine GP still has a shared drive in classification.
- The legacy multi-row `(raceId, driverId)` cases differ in team, car, laps, and finish, so they are distinct entries, not dupes.
- In this dataset, none of the 85 multi-result groups overlap with `qualifying`, `sprint_results`, `lap_times`, or `pit_stops`.

## Changes

- Removed the one-`race_entry`-per-`(year, round, driver_ref)` rule.
- Kept every legacy `results` row as its own `race_entry` + `prix_perf` row.
- Updated the log to report preserved multi-result groups, not dropped rows.

## Rule

- If a driver has multiple legacy result rows in one GP, keep all of them.
- No extra anchor column is needed because no multi-result group overlaps with one-row-per-driver side tables in this dataset.

## Sources

- Forix summary of F1 points/shared-drive history.
- 1958 F1 season reference on no shared-drive points.
- 1960 Argentine GP reference showing a post-1958 shared drive in classification.
