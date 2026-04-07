import java.sql.SQLException;

public final class QualifyingPaceLeaderboardQuery extends BaseQueryAction {
    public QualifyingPaceLeaderboardQuery() {
        super("Qualifying pace leaderboard for a selected season");
    }

    @Override
    public void run(DatabaseClient database, ConsolePrompter prompter) throws SQLException {
        int year = QuerySupport.selectYear(database, prompter);
        QuerySupport.runAndPrint(
                database,
                """
                SELECT TOP 15
                       t.name AS team,
                       COUNT(*) AS timed_entries,
                       CAST(AVG(CAST(
                           CASE
                               WHEN qr.q3_ms IS NOT NULL THEN qr.q3_ms
                               WHEN qr.q2_ms IS NOT NULL THEN qr.q2_ms
                               ELSE qr.q1_ms
                           END AS float
                       )) / 1000.0 AS decimal(10, 3)) AS avg_best_qual_seconds,
                       CAST(MIN(
                           CASE
                               WHEN qr.q3_ms IS NOT NULL THEN qr.q3_ms
                               WHEN qr.q2_ms IS NOT NULL THEN qr.q2_ms
                               ELSE qr.q1_ms
                           END
                       ) / 1000.0 AS decimal(10, 3)) AS best_single_session_seconds
                FROM race_entry re
                JOIN teams t ON t.team_ref = re.team_ref
                JOIN qual_result qr ON qr.entry_id = re.entry_id
                WHERE re.year = ?
                  AND (qr.q1_ms IS NOT NULL OR qr.q2_ms IS NOT NULL OR qr.q3_ms IS NOT NULL)
                GROUP BY t.name
                ORDER BY avg_best_qual_seconds ASC, timed_entries DESC, team
                """,
                statement -> statement.setInt(1, year),
                "Qualifying pace leaderboard for " + year);
    }
}
