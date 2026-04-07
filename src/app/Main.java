import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromArgs(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            printUsage();
            return;
        }

        ConsolePrompter prompter = new ConsolePrompter();
        System.out.println("COMP 3380 Formula 1 SQL Server CLI");
        System.out.println("Connected target: " + config.displaySummary());
        System.out.println("SQLite replica:   " + config.sqliteReplicaPath());

        try (DatabaseClient database = new DatabaseClient(config)) {
            mainLoop(database, prompter);
        } catch (SQLException exception) {
            System.err.println("Database connection failed.");
            System.err.println(exception.getMessage());
        }
    }

    private static void mainLoop(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        while (true) {
            int selection = prompter.promptMenu(
                    "Main menu",
                    List.of(
                            "Run analyst queries",
                            "Browse table data",
                            "Inspect table schema",
                            "Database maintenance",
                            "Show connection details"),
                    false);

            switch (selection) {
                case 0 -> {
                    System.out.println("Exiting.");
                    return;
                }
                case 1 -> runAnalystQueries(database, prompter);
                case 2 -> browseTables(database, prompter);
                case 3 -> inspectSchema(database, prompter);
                case 4 -> runMaintenance(database, prompter);
                case 5 -> printConnectionDetails(database.config());
                default -> throw new IllegalStateException("Unexpected menu option: " + selection);
            }
        }
    }

    private static void runAnalystQueries(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        List<QueryAction> queries = QueryCatalog.analystQueries();
        while (true) {
            int selection = prompter.promptMenu(
                    "Analyst queries",
                    queries.stream().map(QueryAction::label).toList(),
                    true);

            if (selection == 0) {
                return;
            }

            try {
                queries.get(selection - 1).run(database, prompter);
            } catch (IllegalStateException cancelled) {
                System.out.println("Query cancelled.");
            } catch (SQLException exception) {
                System.out.println("Query failed: " + exception.getMessage());
            }
        }
    }

    private static void browseTables(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        DatabaseClient.Option table = chooseTable(database, prompter);
        if (table == null) {
            return;
        }

        int pageSize = defaulted(prompter.promptPositiveInt("Rows per page [default 20]: ", true), 20);
        int page = 0;
        long totalRows = database.countRows(table.id());

        while (true) {
            int offset = page * pageSize;
            CachedTable rows = database.browseTable(table.id(), ProjectSchema.TABLE_ORDER_BY.get(table.id()), pageSize, offset);
            System.out.println();
            System.out.printf("%s rows %d-%d of %d%n",
                    table.id(),
                    totalRows == 0 ? 0 : offset + 1,
                    Math.min(totalRows, offset + rows.rows().size()),
                    totalRows);
            TablePrinter.print(rows);

            String input = prompter.prompt("[n]ext, [p]revious, [b]ack > ").toLowerCase();
            if (input.equals("b")) {
                return;
            }
            if (input.equals("n")) {
                if (offset + pageSize >= totalRows) {
                    System.out.println("Already at the last page.");
                } else {
                    page++;
                }
                continue;
            }
            if (input.equals("p")) {
                if (page == 0) {
                    System.out.println("Already at the first page.");
                } else {
                    page--;
                }
                continue;
            }
            System.out.println("Enter n, p, or b.");
        }
    }

    private static void inspectSchema(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        DatabaseClient.Option table = chooseTable(database, prompter);
        if (table == null) {
            return;
        }

        CachedTable schema = database.inspectTableColumns(table.id());
        long rowCount = database.countRows(table.id());
        System.out.printf("%nSchema for %s (%d rows)%n", table.id(), rowCount);
        TablePrinter.print(schema);
    }

    private static void runMaintenance(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        while (true) {
            int selection = prompter.promptMenu(
                    "Database maintenance",
                    List.of(
                            "Delete all data from the loaded tables",
                            "Rebuild tables and repopulate from the local SQLite replica"),
                    true);

            if (selection == 0) {
                return;
            }

            switch (selection) {
                case 1 -> deleteAllData(database, prompter);
                case 2 -> repopulateDatabase(database.config(), prompter);
                default -> throw new IllegalStateException("Unexpected maintenance option: " + selection);
            }
        }
    }

    private static void deleteAllData(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        if (!prompter.confirm("Delete every row from the project tables in " + database.config().database() + "?")) {
            System.out.println("Delete cancelled.");
            return;
        }

        long start = System.nanoTime();
        database.deleteAllData(ProjectSchema.DROP_ORDER);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        System.out.printf("All project tables cleared in %d ms.%n", elapsedMillis);
    }

    private static void repopulateDatabase(AppConfig config, ConsolePrompter prompter) {
        if (!prompter.confirm("Rebuild the tables in " + config.database() + " from the SQLite replica?")) {
            System.out.println("Repopulation cancelled.");
            return;
        }

        try {
            new SqliteReplicaLoader(config).rebuildFromSqlite();
            System.out.println("Repopulation completed.");
        } catch (IOException exception) {
            System.out.println("Could not read a required local file: " + exception.getMessage());
        } catch (SQLException exception) {
            System.out.println("Repopulation failed: " + exception.getMessage());
        }
    }

    private static DatabaseClient.Option chooseTable(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        List<DatabaseClient.Option> tables = database.listTables();
        while (true) {
            int selection = prompter.promptMenu(
                    "Choose a table",
                    tables.stream().map(DatabaseClient.Option::label).toList(),
                    true);
            if (selection == 0) {
                return null;
            }

            DatabaseClient.Option table = tables.get(selection - 1);
            if (!ProjectSchema.TABLE_ORDER_BY.containsKey(table.id())) {
                System.out.println("That table is not configured for browsing yet.");
                continue;
            }
            return table;
        }
    }

    private static void printConnectionDetails(AppConfig config) {
        System.out.println();
        System.out.println("Connection details");
        System.out.println("Host:            " + config.host());
        System.out.println("Port:            " + config.port());
        System.out.println("Database:        " + config.database());
        System.out.println("Username:        " + config.username());
        System.out.println("Encrypt:         " + config.encrypt());
        System.out.println("Trust cert:      " + config.trustServerCertificate());
        System.out.println("Project root:    " + config.projectRoot());
        System.out.println("SQLite replica:  " + config.sqliteReplicaPath());
        System.out.println("Schema path:     " + config.schemaPath());
        System.out.println("Batch size:      " + config.batchSize());
    }

    private static int defaulted(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    public static void printUsage() {
        System.out.println("Usage: java -cp build/classes:lib/* Main [options]");
        System.out.println("Options:");
        System.out.println("  --host HOST");
        System.out.println("  --port PORT");
        System.out.println("  --database NAME");
        System.out.println("  --username USER");
        System.out.println("  --password PASSWORD");
        System.out.println("  --project-root PATH");
        System.out.println("  --sqlite-replica PATH");
        System.out.println("  --schema PATH");
        System.out.println("  --encrypt true|false");
        System.out.println("  --trust-server-certificate true|false");
        System.out.println("  --login-timeout SECONDS");
        System.out.println("  --batch-size ROWS");
    }
}
