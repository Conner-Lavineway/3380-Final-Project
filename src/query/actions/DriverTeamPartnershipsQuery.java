import java.sql.SQLException;

public final class DriverTeamPartnershipsQuery extends QueryAction {
    public DriverTeamPartnershipsQuery() {
        super("Longest-running driver/team partnerships");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       d.name AS driver,
                       t.name AS team,
                       COUNT(*) AS entries,
                       COUNT(DISTINCT re.year) AS seasons,
                       MIN(re.year) AS first_year,
                       MAX(re.year) AS last_year
                FROM race_entry re
                JOIN drivers d ON d.driver_ref = re.driver_ref
                JOIN teams t ON t.team_ref = re.team_ref
                GROUP BY d.name, t.name
                HAVING COUNT(*) >= 5
                ORDER BY entries DESC, seasons DESC, driver, team
                """,
                null,
                "Longest-running driver and team partnerships");
    }
}
