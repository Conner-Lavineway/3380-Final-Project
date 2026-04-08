import java.sql.SQLException;

public final class PitHeavyWeekendsQuery extends QueryAction {
    public PitHeavyWeekendsQuery() {
        super("Most pit-heavy race weekends for a selected season");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       re.round,
                       c.name AS circuit,
                       c.country,
                       COUNT(ps.stop_no) AS 'total pit stops',
                       COUNT(DISTINCT re.entry_id) AS starters,
                       CAST(
                           COUNT(ps.stop_no) * 1.0 / NULLIF(COUNT(DISTINCT re.entry_id), 0) AS decimal(10, 2)
                       ) AS 'stops per entry'
                FROM race_entry re
                JOIN race_weekend rw ON rw.year = re.year AND rw.round = re.round
                JOIN circuits c ON c.circuit_id = rw.circuit_id
                LEFT JOIN pit_stop ps ON ps.entry_id = re.entry_id
                WHERE re.year = ?
                GROUP BY re.round, c.name, c.country
                ORDER BY "total pit stops" DESC, "stops per entry" DESC, re.round
                """,
                statement -> statement.setInt(1, year),
                "Most pit-heavy weekends for " + year);
    }
}
