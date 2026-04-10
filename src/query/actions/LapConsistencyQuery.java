import java.sql.SQLException;

public final class LapConsistencyQuery extends QueryAction {
    public LapConsistencyQuery() {
        super("Lap consistency for a selected race weekend");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        int round = QuerySupport.selectRound(database, prompter, year);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       d.name AS driver,
                       t.name AS team,
                       COUNT(li.lap_num) AS 'recorded laps',
                       CAST(AVG(CAST(li.lap_time_ms AS float)) / 1000.0 AS decimal(10, 3)) AS 'average lap (secs)',
                       CAST(STDEV(CAST(li.lap_time_ms AS float)) / 1000.0 AS decimal(10, 3)) AS 'lap standard-deviation (secs)',
                       MIN(li.position) AS 'best running position'
                FROM race_entry re
                JOIN drivers d ON d.driver_ref = re.driver_ref
                JOIN teams t ON t.team_ref = re.team_ref
                JOIN lap_info li ON li.entry_id = re.entry_id
                WHERE re.year = ?
                  AND re.round = ?
                  AND li.lap_time_ms IS NOT NULL
                GROUP BY d.name, t.name
                HAVING COUNT(li.lap_num) >= 5
                ORDER BY "lap standard-deviation (secs)" ASC, "average lap (secs)" ASC, driver
                """,
                statement -> {
                    statement.setInt(1, year);
                    statement.setInt(2, round);
                },
                "Lap consistency for " + year + " round " + round);
    }
}
