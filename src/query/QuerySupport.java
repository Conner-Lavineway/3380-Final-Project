import java.sql.SQLException;
import java.util.List;

public final class QuerySupport {
    private QuerySupport() {
    }

    public static int selectYear(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        List<DatabaseClient.Option> years = database.listYears();
        return Integer.parseInt(selectOption(prompter, "Choose a season", years).id());
    }

    public static int selectRound(DatabaseClient database, ConsolePrompter prompter, int year) throws SQLException {
        List<DatabaseClient.Option> rounds = database.listRoundsForYear(year);
        return Integer.parseInt(selectOption(prompter, "Choose a race weekend", rounds).id());
    }

    public static DatabaseClient.Option selectDriver(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        while (true) {
            String search = prompter.prompt("Driver search (blank lists first 25 alphabetically): ");
            List<DatabaseClient.Option> drivers = database.searchDrivers(search);
            if (drivers.isEmpty()) {
                System.out.println("No drivers matched that search.");
                continue;
            }
            return selectOption(prompter, "Choose a driver", drivers);
        }
    }

    public static DatabaseClient.Option selectOption(
            ConsolePrompter prompter,
            String title,
            List<DatabaseClient.Option> options) {
        while (true) {
            int selected = prompter.promptMenu(
                    title,
                    options.stream().map(DatabaseClient.Option::label).toList(),
                    true);
            if (selected == 0) {
                throw new IllegalStateException("Selection cancelled by user.");
            }
            return options.get(selected - 1);
        }
    }

    public static void runAndPrint(
            DatabaseClient database,
            String sql,
            DatabaseClient.StatementBinder binder,
            String description) throws SQLException {
        long start = System.nanoTime();
        CachedTable table = database.query(sql, binder);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        System.out.println();
        System.out.println(description);
        TablePrinter.print(table);
        System.out.printf("Query time: %d ms%n", elapsedMillis);
    }
}
