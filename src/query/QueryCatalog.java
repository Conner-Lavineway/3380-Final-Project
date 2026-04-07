import java.util.List;

public final class QueryCatalog {
    private QueryCatalog() {
    }

    public static List<QueryAction> analystQueries() {
        return List.of(
                new SeasonCalendarQuery(),
                new CircuitFrequencyQuery(),
                new DriverCareerSummaryQuery(),
                new TeamSeasonWorkloadQuery(),
                new DriverTeamPartnershipsQuery(),
                new QualifyingPaceLeaderboardQuery(),
                new TeamReliabilityQuery(),
                new CircuitAttritionQuery(),
                new PitStopBurdenQuery(),
                new PitHeavyWeekendsQuery(),
                new SprintVsRegularSummaryQuery(),
                new LapConsistencyQuery());
    }
}
