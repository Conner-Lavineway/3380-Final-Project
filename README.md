# 3380-Final-Project

## Hard requirements

* Dataset graph must be **connected**
* Avoid wheel-and-spoke design
* Target: **10+ main tables**
* Target: **1000+ total rows**
* Most tables should connect to **2+ other tables**
* Support/lookup tables do **not** count toward the 10
* Dataset must be **≤ 300 MB**
* Can use multiple sources if everything stays connected
* Can generate **supplemental** data only
* Must clearly mark generated vs real data
* Cannot generate the whole dataset

## Stage 1 — Database design

* Due **Mar 20**
* **1%**
* Submit **1 PDF**
* Include:

  * 3–5 paragraph data summary
  * why chosen
  * row / column / table counts
  * source links
  * ER/EER diagram
  * participation + cardinality constraints
  * brief justifications
  * final normalized relational model
  * brief translation + normalization notes

## Stage 2 — Presentation

* Due **Apr 7**
* **2%**
* **5 min** demo
* Show:

  * project summary
  * runs on **aviary**
  * connects to populated DB on **uranium**
  * executes queries
  * presents results
  * handles invalid input
  * robust against SQL injection
* Either live demo or video

## Stage 3 — Interface

* Due **Apr 10**
* **3%**
* DB must live on **uranium.cs.umanitoba.ca**
* Must use **code-based population**
* App must be **Java, Python, or website**
* Must run on **aviary without installs**
* Interface requirements:

  * no unsafe free-form SQL
  * analyst can ask interesting questions
  * all tables accessible somehow
  * results ordered, labeled, readable
  * paging or limited output
  * delete-all + repopulate option
* Submit ZIP with:

  * schema / table creation
  * relationships
  * population code
  * interface code
  * `readme.md`
  * DB userid + password

## Stage 4 — Final report

* Due **Apr 10**
* **4%**
* Submit **1 PDF**
* Include:

  * cover page with names + userids
  * 1-paragraph intro
  * data summary
  * attributes / size / preprocessing / sources
  * ER diagram
  * data model discussion
  * tricky design decisions
  * fit to relational model
  * DB / SQL flavour discussion
  * interface description + screenshots
  * interesting query list
  * what each query returns
  * why analyst cares
  * conclusion on whether relational DB was a good fit
  * teaching value for COMP 3380
  * lessons learned
  * appendix with each member’s contributions by stage

## Query expectations

* At least **12 interesting queries**
* “Interesting” = analyst would care + hard to do manually
* Include some simpler access/summarization queries too
* Should include:

  * aggregate function query
  * `GROUP BY`
  * `ORDER BY`
* Query efficiency not the focus, but should run in a few seconds
* If query takes time, interface should indicate it
* Prefer parameter selection from lists over free text when possible
* Any string input must be safe from SQL injection

## Optional Nix development shell

This repo includes a `flake.nix` and `flake.lock` so anyone using Nix can get the same Java and SQL tooling versions.

- Enter the shell with `nix develop`
- Leave it with `exit`
- If you do not use Nix, you can ignore these files safely
