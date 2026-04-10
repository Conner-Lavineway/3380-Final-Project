#set page(
  width: 13.333in,
  height: 7.5in,
  margin: (x: 0.65in, y: 0.5in),
  fill: rgb("#f5efe4"),
)

#set text(
  font: "Liberation Sans",
  size: 16pt,
  fill: rgb("#1f2933"),
)

#set par(justify: false, leading: 0.72em)

#let ink = rgb("#1f2933")
#let rust = rgb("#9a3412")
#let steel = rgb("#1d4ed8")
#let muted = rgb("#475569")
#let panel = rgb("#fffaf3")
#let linec = rgb("#d6c5ab")

#let slide(title, body) = [
  #text(size: 28pt, weight: "bold", fill: ink)[#title]
  #v(6pt)
  #line(length: 100%, stroke: (paint: rust, thickness: 1.4pt))
  #v(16pt)
  #body
]

#let card(body) = block(
  fill: panel,
  stroke: (paint: linec, thickness: 0.8pt),
  inset: 12pt,
  radius: 8pt,
  width: 100%,
)[#body]

#let mono(body) = text(font: "Liberation Mono", size: 13pt, fill: ink)[#body]

#slide(
  [Formula 1 Data Platform],
  [
    #v(0.35in)
    #text(size: 34pt, weight: "bold", fill: rust)[Schema Changes and Interface]
    #v(0.25in)
    #text(size: 18pt, fill: muted)[From the legacy SQLite dataset to a SQL Server CLI for analysts]
    #v(0.45in)
    #grid(
      columns: (1fr, 1fr, 1fr),
      gutter: 18pt,
      card[
        #text(weight: "bold", fill: steel)[Dataset]
        Legacy F1 data in SQLite
      ],
      card[
        #text(weight: "bold", fill: steel)[Schema]
        Refactored around `race_entry`
      ],
      card[
        #text(weight: "bold", fill: steel)[Interface]
        Terminal interface on SQL Server
      ],
    )
  ],
)

#pagebreak()

#slide(
  [1. System Overview],
  [
    - The implementation centers on two technical pieces:
      - a schema that is easier to load and query in SQL Server
      - an interface that lets an analyst browse data without writing SQL
    - The pipeline is:
      #v(6pt)
      #card[
        #mono([Original SQLite replica -> refactored schema -> loader program -> SQL Server -> terminal interface])
      ]
    - The interface emphasizes:
      - safe parameter selection
      - readable ordered output
      - access to every table
      - guided workflows instead of free-form commands
  ],
)

#pagebreak()

#slide(
  [2. Why The Schema Changed],
  [
    - A good example is the race-result path.
    #v(8pt)
    #table(
      columns: 2,
      inset: 8pt,
      stroke: (paint: linec, thickness: 0.7pt),
      fill: (x, y) => if y == 0 { rgb("#efe2cf") } else { panel },
      [Version], [Shape],
      [Original raw data], [`results(raceId, driverId, teamId, number, laps, time, fastestLapTime, statusId, ...)`],
      [Stage 1], [`prix_perf(year, round, driverRef, racetime, lapNum, fLapTime, fLapNum, statusID)`],
      [Current], [`race_entry(entry_id, year, round, driver_ref, team_ref, car_number)` + `prix_perf(entry_id, race_time, num_laps, fastest_lap_time_ms, status_id)`],
    )
    #v(12pt)
    #card[
      #text(weight: "bold", fill: rust)[Design point:]
      the raw table mixed participation facts with performance facts; the current schema separates them cleanly.
    ]
  ],
)

#pagebreak()

#slide(
  [3. Core Refactor: `race_entry`],
  [
    #grid(
      columns: (1.05fr, 0.95fr),
      gutter: 18pt,
      card[
        #text(weight: "bold", fill: steel)[New anchor table]
        #v(6pt)
        #mono([race_entry(entry_id, year, round, driver_ref, team_ref, car_number)])
        #v(10pt)
        One row means one driver's entry in one weekend for one team and car number.
      ],
      card[
        #text(weight: "bold", fill: steel)[Why it helps]
        - `race_entry` stores who entered the weekend
        - `prix_perf` stores what happened in the race
        - child tables get one stable foreign key instead of repeated composite keys
        - the model stays clear even when historical participation gets messy
      ],
    )
    #v(14pt)
    #table(
      columns: 2,
      inset: 8pt,
      stroke: (paint: linec, thickness: 0.7pt),
      fill: (x, y) => if y == 0 { rgb("#efe2cf") } else { panel },
      [Child table], [Now references],
      [`qual_result`], [`entry_id`],
      [`sprint_perf`], [`entry_id`],
      [`prix_perf`], [`entry_id`],
      [`lap_info`], [`entry_id`],
      [`pit_stop`], [`entry_id`],
    )
  ],
)

#pagebreak()

#slide(
  [4. Resulting Schema Shape],
  [
    - The implemented schema has 13 main tables:
      `circuits`, `drivers`, `teams`, `status`, `race_weekend`, `sprint_weekend`, `regular_weekend`, `race_entry`, `qual_result`, `sprint_perf`, `prix_perf`, `lap_info`, `pit_stop`.
    - Race weekends are split into shared weekend facts plus sprint-only and regular-only tables.
    - Child performance tables use `ON DELETE CASCADE` from `race_entry`, which keeps cleanup predictable.
    - The design adds explicit checks on years, rounds, lap numbers, car numbers, and duration fields.
    #v(8pt)
    #card[
      #text(weight: "bold", fill: rust)[Net effect:]
      fewer repeated identifiers, clearer foreign-key chains, and a schema that maps cleanly into code.
    ]
  ],
)

#pagebreak()

#slide(
  [5. Portability Decisions],
  [
    - The schema is written to move from SQLite into SQL Server with minimal translation.
    - It uses portable relational types such as `DATE`, `TIME`, `VARCHAR`, `SMALLINT`, and `INTEGER`.
    - True durations are normalized into integer milliseconds:
      - qualifying segments
      - fastest laps
      - lap times
      - pit-stop duration
    - Classification-style race time stays as text because the source mixes elapsed times, gaps, and labels like `+1 Lap`.
    #v(10pt)
    #grid(
      columns: (1fr, 1fr),
      gutter: 14pt,
      card[
        #text(weight: "bold", fill: steel)[Portable]
        `DATE`, `TIME`, `VARCHAR`, `SMALLINT`
      ],
      card[
        #text(weight: "bold", fill: steel)[Deliberately textual]
        `race_time` in sprint and prix results
      ],
    )
  ],
)

#pagebreak()

#slide(
  [6. Loader Architecture],
  [
    - A non-interactive Java loader is used to populate SQL Server from the SQLite replica.
    - A configuration layer resolves paths and connection settings from command-line arguments or environment variables.
    - The loader then runs a fixed workflow:
    #v(8pt)
    #card[
      #mono([validate config -> ensure database exists -> rebuild schema -> load tables in dependency order -> create indexes -> commit])
    ]
    - The loader opens SQLite as the source and SQL Server as the target in the same run.
    - If anything fails during rebuild or load, the SQL Server transaction rolls back.
  ],
)

#pagebreak()

#slide(
  [7. Why The Loader Is Structured This Way],
  [
    - Table load order is explicit because foreign keys matter.
    - Index creation happens after bulk insert, which keeps the import simpler and faster.
    - Batch inserts are configurable, so the same code can run in different environments without changing the loader logic.
    - The loader can create the target database if it does not exist yet.
    #v(10pt)
    #table(
      columns: 2,
      inset: 8pt,
      stroke: (paint: linec, thickness: 0.7pt),
      fill: (x, y) => if y == 0 { rgb("#efe2cf") } else { panel },
      [Concern], [Handling],
      [Missing DB], [create DB and wait until online],
      [FK dependencies], [fixed `TABLE_ORDER` and `DROP_ORDER`],
      [Large loads], [batched prepared inserts],
      [Failure mid-load], [rollback instead of partial state],
    )
  ],
)

#pagebreak()

#slide(
  [8. Interface Overview],
  [
    - The interface is a Java terminal application organized around guided menus.
    - The main menu centers on the three analyst-facing paths:
      - analyst queries
      - browse table data
      - inspect table schema
    - A prompt layer validates terminal input.
    - A database access layer sends parameterized SQL to SQL Server.
    - A formatter renders query results as fixed-width tables.
    #v(10pt)
    #card[
      #mono([menu layer -> query action layer -> database layer -> cached result -> table formatter])
    ]
  ],
)

#pagebreak()

#slide(
  [9. Analyst Query Interface],
  [
    - The CLI exposes 12 analyst-oriented queries through a menu catalog.
    - A `QueryAction` abstraction was implemented in Java so each query can carry a label and run its own SQL against the database.
    - Queries are selected by number rather than typed as SQL.
    - Where parameters are needed, the user chooses from guided inputs:
      - season list
      - weekend list for a chosen season
      - driver search with capped matches
    - This keeps the interface simple for analysts while still allowing nontrivial summaries and leaderboards.
    #v(8pt)
    #grid(
      columns: (1fr, 1fr),
      gutter: 14pt,
      card[
        #text(weight: "bold", fill: steel)[Examples]
        - season calendar
        - driver career summary
        - team reliability
        - lap consistency
      ],
      card[
        #text(weight: "bold", fill: steel)[Shared query support]
        prompt helpers, option selection, result printing, and query timing
      ],
    )
  ],
)

#pagebreak()

#slide(
  [10. Table Access and Readability],
  [
    - Every base table is accessible from the CLI.
    - Browse mode paginates results and lets the user choose a page size.
    - Schema inspection shows each column's type, nullability, and declared length.
    - A schema metadata layer provides stable ordering rules so paged browsing is deterministic.
    - The output formatter truncates long values and aligns columns for terminal readability.
    #v(8pt)
    #card[
      #mono([race_entry rows 1-20 of N | next / previous / back])
    ]
  ],
)

#pagebreak()

#slide(
  [11. Safety and Robustness],
  [
    - Analyst-facing queries use prepared statements with bound parameters.
    - Search input is still parameterized; the code binds wildcard patterns instead of concatenating user strings.
    - Table names are validated before browse and count operations.
    - Destructive actions require explicit confirmation.
    - Invalid input loops back to a clear prompt instead of crashing the program.
    #v(10pt)
    #grid(
      columns: (1fr, 1fr),
      gutter: 14pt,
      card[
        #text(weight: "bold", fill: steel)[Examples]
        - `promptMenu` accepts only valid menu numbers
        - `confirm` accepts only yes/no variants
      ],
      card[
        #text(weight: "bold", fill: steel)[Security stance]
        no free-form SQL entry and stronger resistance to SQL injection
      ],
    )
  ],
)

#pagebreak()

#slide(
  [12. Closing View],
  [
    #text(weight: "bold", fill: rust)[System walkthrough]
    - `race_entry` is the structural center of the schema
    - the loader moves data from SQLite into SQL Server in dependency order
    - the CLI exposes queries, schema inspection, and table browsing through guided menus
    - query execution, paging, and validation are all handled inside the application layer
    #v(12pt)
    #text(weight: "bold", fill: rust)[Main takeaway]
    The schema refactor and the interface design work together: one makes the dataset easier to represent correctly, and the other makes it easier to explore safely.
  ],
)
