import java.sql.SQLException;

public final class TeamSeasonWorkloadQuery extends QueryAction {
    public TeamSeasonWorkloadQuery() {
        super("Team workload summary for a selected season");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT t.name AS team,
                       COUNT(*) AS entries,
                       COUNT(DISTINCT re.driver_ref) AS drivers_used,
                       COUNT(DISTINCT re.round) AS weekends,
                       CAST(AVG(CAST(pp.num_laps AS float)) AS decimal(10, 2)) AS avg_prix_laps,
                       CAST(AVG(CAST(sp.num_laps AS float)) AS decimal(10, 2)) AS avg_sprint_laps
                FROM race_entry re
                JOIN teams t ON t.team_ref = re.team_ref
                LEFT JOIN prix_perf pp ON pp.entry_id = re.entry_id
                LEFT JOIN sprint_perf sp ON sp.entry_id = re.entry_id
                WHERE re.year = ?
                GROUP BY t.name
                ORDER BY entries DESC, drivers_used DESC, team
                """,
                statement -> statement.setInt(1, year),
                "Team workload summary for " + year);
    }
}
