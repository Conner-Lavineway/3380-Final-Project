import java.sql.SQLException;

public final class SeasonCalendarQuery extends QueryAction {
    public SeasonCalendarQuery() {
        super("Season calendar for a selected year");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT rw.round,
                       c.name AS circuit,
                       c.country,
                       rw.prix_date,
                       CASE WHEN sw.year IS NULL THEN 'Regular' ELSE 'Sprint' END AS weekend_type
                FROM race_weekend rw
                JOIN circuits c ON c.circuit_id = rw.circuit_id
                LEFT JOIN sprint_weekend sw ON sw.year = rw.year AND sw.round = rw.round
                WHERE rw.year = ?
                ORDER BY rw.round
                """,
                statement -> statement.setInt(1, year),
                "Season calendar for " + year);
    }
}
