import java.sql.SQLException;

public final class TeamReliabilityQuery extends BaseQueryAction {
    public TeamReliabilityQuery() {
        super("Team reliability leaderboard for a selected season");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT t.name AS team,
                       COUNT(*) AS total_entries,
                       SUM(CASE WHEN s.description = 'Finished' OR s.description LIKE '+%' THEN 1 ELSE 0 END) AS classified_finishes,
                       SUM(CASE WHEN s.description = 'Finished' OR s.description LIKE '+%' THEN 0 ELSE 1 END) AS retirements,
                       CAST(
                           100.0 * SUM(CASE WHEN s.description = 'Finished' OR s.description LIKE '+%' THEN 1 ELSE 0 END)
                           / COUNT(*) AS decimal(5, 2)
                       ) AS classified_finish_pct
                FROM race_entry re
                JOIN teams t ON t.team_ref = re.team_ref
                JOIN prix_perf pp ON pp.entry_id = re.entry_id
                JOIN status s ON s.status_id = pp.status_id
                WHERE re.year = ?
                GROUP BY t.name
                ORDER BY classified_finish_pct DESC, total_entries DESC, team
                """,
                statement -> statement.setInt(1, year),
                "Team reliability for " + year);
    }
}
