import java.sql.SQLException;

public final class SprintVsRegularSummaryQuery extends QueryAction {
    public SprintVsRegularSummaryQuery() {
        super("Sprint versus regular season summary");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 20
                       rw.year,
                       COUNT(DISTINCT rw.round) AS 'total weekends',
                       SUM(CASE WHEN sw.year IS NULL THEN 0 ELSE 1 END) AS 'sprint weekends',
                       SUM(CASE WHEN sw.year IS NULL THEN 1 ELSE 0 END) AS 'regular weekends',
                       COUNT(DISTINCT re.entry_id) AS 'race entries',
                       CAST(AVG(CAST(pp.num_laps AS float)) AS decimal(10, 2)) AS 'avg prix laps'
                FROM race_weekend rw
                LEFT JOIN sprint_weekend sw ON sw.year = rw.year AND sw.round = rw.round
                LEFT JOIN race_entry re ON re.year = rw.year AND re.round = rw.round
                LEFT JOIN prix_perf pp ON pp.entry_id = re.entry_id
                GROUP BY rw.year
                ORDER BY rw.year DESC
                """,
                null,
                "Sprint versus regular weekend summary");
    }
}
