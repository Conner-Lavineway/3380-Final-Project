Source layout:

- `app/`
  - `Main.java`: interactive CLI entry point
  - `LoaderMain.java`: non-interactive DB rebuild entry point used by `make db-load`
- `config/`
  - `AppConfig.java`: reads command-line args and environment variables
- `db/`
  - `DatabaseClient.java`: SQL Server query/browse/delete helper
  - `SqliteReplicaLoader.java`: rebuilds SQL Server from the local SQLite replica
  - `ProjectSchema.java`: shared table order, browse order, and index definitions
- `query/`
  - `QueryCatalog.java`: single list of the analyst queries shown in the menu
  - `QueryAction.java`: base type for one menu query
  - `QuerySupport.java`: shared prompting and result-printing helpers for queries
  - `actions/`: one Java file per analyst query
- `ui/`
  - `ConsolePrompter.java`: terminal input helper
  - `CachedTable.java`: in-memory query result
  - `TablePrinter.java`: fixed-width table output

