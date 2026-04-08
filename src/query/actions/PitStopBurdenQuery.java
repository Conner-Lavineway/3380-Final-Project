import java.sql.SQLException;

public final class PitStopBurdenQuery extends QueryAction {
    public PitStopBurdenQuery() {
        super("Pit-stop burden by team for a selected season");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       t.name AS team,
                       COUNT(ps.stop_no) AS 'total pit stops',
                       CAST(AVG(CAST(ps.duration_ms AS float)) / 1000.0 AS decimal(10, 3)) AS 'avg stop seconds',
                       CAST(
                           COUNT(ps.stop_no) * 1.0 / NULLIF(COUNT(DISTINCT re.entry_id), 0) AS decimal(10, 2)
                       ) AS 'stops per entry'
                FROM race_entry re
                JOIN teams t ON t.team_ref = re.team_ref
                LEFT JOIN pit_stop ps ON ps.entry_id = re.entry_id
                WHERE re.year = ?
                GROUP BY t.name
                HAVING COUNT(ps.stop_no) > 0
                ORDER BY "stops per entry" DESC, "avg stop seconds" DESC, team
                """,
                statement -> statement.setInt(1, year),
                "Pit-stop burden by team for " + year);
    }
}
