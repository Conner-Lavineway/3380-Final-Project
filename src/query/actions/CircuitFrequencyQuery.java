import java.sql.SQLException;

public final class CircuitFrequencyQuery extends BaseQueryAction {
    public CircuitFrequencyQuery() {
        super("Circuits with the most Grand Prix weekends");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       c.name AS circuit,
                       c.country,
                       COUNT(*) AS grand_prix_weekends,
                       MIN(rw.year) AS first_year,
                       MAX(rw.year) AS last_year
                FROM race_weekend rw
                JOIN circuits c ON c.circuit_id = rw.circuit_id
                GROUP BY c.name, c.country
                ORDER BY grand_prix_weekends DESC, last_year DESC, circuit
                """,
                null,
                "Most frequently used circuits");
    }
}
