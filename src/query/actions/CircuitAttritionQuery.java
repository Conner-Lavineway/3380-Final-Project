import java.sql.SQLException;

public final class CircuitAttritionQuery extends QueryAction {
    public CircuitAttritionQuery() {
        super("Circuit attrition hotspots across the dataset");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       c.name AS circuit,
                       c.country,
                       COUNT(*) AS 'total entries',
                       SUM(CASE WHEN s.description = 'Finished' OR s.description LIKE '+%' THEN 0 ELSE 1 END) AS retirements,
                       CAST(
                           100.0 * SUM(CASE WHEN s.description = 'Finished' OR s.description LIKE '+%' THEN 0 ELSE 1 END)
                           / COUNT(*) AS decimal(5, 2)
                       ) AS 'retirement (%)'
                FROM race_entry re
                JOIN race_weekend rw ON rw.year = re.year AND rw.round = re.round
                JOIN circuits c ON c.circuit_id = rw.circuit_id
                JOIN prix_perf pp ON pp.entry_id = re.entry_id
                JOIN status s ON s.status_id = pp.status_id
                GROUP BY c.name, c.country
                HAVING COUNT(DISTINCT CONCAT(rw.year, '-', rw.round)) >= 10
                ORDER BY "retirement (%)" DESC, retirements DESC, circuit
                """,
                null,
                "Circuit attrition hotspots");
    }
}
