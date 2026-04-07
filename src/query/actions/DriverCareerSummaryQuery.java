import java.sql.SQLException;

public final class DriverCareerSummaryQuery extends QueryAction {
    public DriverCareerSummaryQuery() {
        super("Career summary for a selected driver");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        DatabaseClient.Option driver = QuerySupport.selectDriver(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT d.name AS driver,
                       d.nationality,
                       COUNT(*) AS race_entries,
                       COUNT(DISTINCT re.year) AS seasons,
                       COUNT(DISTINCT re.team_ref) AS teams_used,
                       MIN(re.year) AS first_year,
                       MAX(re.year) AS last_year
                FROM drivers d
                JOIN race_entry re ON re.driver_ref = d.driver_ref
                WHERE d.driver_ref = ?
                GROUP BY d.name, d.nationality
                """,
                statement -> statement.setString(1, driver.id()),
                "Career summary for " + driver.label());
    }
}
