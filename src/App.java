import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

public final class App {
    private App() {
    }

    private static TreeMap<Integer, Ballot> sBallots = new TreeMap<>();
    public static TreeMap<String, TreeMap<String, ArrayList<Ballot>>> sAllVotes = new TreeMap<>();// first string is
                                                                                                 // election, second is
                                                                                                 // candidate
    public static ArrayList<String> sImportantElections = new ArrayList<>();
    public static TreeMap<String, String> sWinners = new TreeMap<>();
    public static String[] sColumnHeadings;
    public static PrintWriter sWriter;

    public static void main(String[] args) throws Exception {
        System.out.println("Looking in " + System.getProperty("user.dir") + " for voteTallies.csv");
        BufferedReader reader;

        reader = new BufferedReader(new FileReader("voteTallies.csv"));
        sWriter = new PrintWriter(new FileWriter("voteResults.txt"), true);

        String line;
        int lineIdx = 1;
        while ((line = reader.readLine()) != null) {
            line = line.replace("\"", "");// google sheets adds a bunch of random quotes

            if (line.contains("Timestamp")) {
                sWriter.println("Processing Headings");
                sColumnHeadings = line.split(",");
                for (String heading : Arrays.asList(sColumnHeadings)) {
                    if (heading.contains("Timestamp") || heading.contains("Email Address")
                            || heading.contains("Full Name")) {
                        continue;
                    }
                    String election = getElectionNameFromHeading(heading);// remove first/second/third
                    sWriter.println("Found column " + heading);

                    if (!sAllVotes.containsKey(election)) {
                        sAllVotes.put(election, new TreeMap<>());
                        sWriter.println("Created election " + election);
                        if (election.contains("Captain")) {
                            sImportantElections.add(election);
                            sWriter.println("Important election " + election + " noted");
                        }
                    }
                }
                sWriter.println("Elections: " + sAllVotes.keySet().toString());
                sWriter.println("Important elections: " + sImportantElections.toString());
            } else {
                sBallots.put(lineIdx, new Ballot(line, lineIdx));
                sWriter.println("Found ballot " + line + ". Choices: " + sBallots.get(lineIdx).toString());
                lineIdx++;
            }

        }

        reader.close();

        for (String importantName : sImportantElections) {
            runElection(importantName);
            System.out.println("Running election " + importantName);
            if (sWinners.containsKey(importantName)) { // a winner has been found so they need to be removed from the
                                                      // list of eligible candidates
                sAllVotes.keySet().forEach((electionName) -> {
                    deregisterCandidate(electionName, sWinners.get(importantName));
                });

            }
        }

        for (Map.Entry<String, TreeMap<String, ArrayList<Ballot>>> elections : sAllVotes.entrySet()) {
            if (!sImportantElections.contains(elections.getKey())) {
                System.out.println("Running election " + elections.getKey());
                runElection(elections.getKey());
            }
        }

        sWriter.println("Results:");
        sWinners.forEach((election, winner) -> {
            sWriter.println("    " + election + ": " + winner);
        });

        sWriter.flush();
        sWriter.close();
        System.out.println("Done");
    }

    public static String getElectionNameFromHeading(String heading) {
        return (heading.replaceAll(" \\[.*", ""));
    }

    public static void runElection(String election) throws IOException {
        sWriter.println("Running election " + election);

        sBallots.forEach((idx, ballot) -> {
            try {
                ballot.cast(election);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        sWriter.println("Initial casting complete");
        printElectionStandings(election);

        for (int round = 0; round <= 10; round++) {
            int numVotes = getNumberOfVotesInElection(election);
            sWriter.println(numVotes + " votes in election " + election);
            SimpleEntry<String, Integer> currentLeader = getCurrentLeader(election);
            if (currentLeader.getValue() > numVotes / 2 && currentLeader.getKey() != null) {
                sWriter.println("Winner found. Winner is " + currentLeader.getKey() + " with " + currentLeader.getValue()
                        + " votes.");
                sWinners.put(election, currentLeader.getKey());
                break;
            }
            int numberOfCandidatesBeforeRemoval = sAllVotes.get(election).size();
            SimpleEntry<String, Integer> currentLoser = getCurrentLoser(election);
            if (currentLoser.getKey() == null) {
                sWriter.println("Unbroken tie for the lose. See standings and resolve manually.");
                break;
            }
            ArrayList<Ballot> votesForLoser = sAllVotes.get(election).get(currentLoser.getKey());
            sAllVotes.get(election).remove(currentLoser.getKey());
            sWriter.println("Candidate " + currentLoser.getKey() + " removed.");

            for (Ballot ballotForLoser : votesForLoser) { // now they are eliminated remove those that voted for them
                ballotForLoser.cast(election);
            }

            if (sAllVotes.get(election).size() >= numberOfCandidatesBeforeRemoval) {
                System.err.println("Recast all votes for " + currentLoser + " but now there are more candidates");
                System.exit(-5);
            }
            sWriter.println("Starting round " + (round + 2));
            printElectionStandings(election);
        }

        sWriter.println("Finished election " + election);
    }

    public static ArrayList<Map.Entry<String, ArrayList<Ballot>>> getSortedStandings(String election) {
        ArrayList<Map.Entry<String, ArrayList<Ballot>>> currentStandings = new ArrayList<>(
                sAllVotes.get(election).entrySet());
        currentStandings.sort(Entry.comparingByValue((ArrayList<Ballot> o1, ArrayList<Ballot> o2) -> {
            return (int) Math.signum(o2.size() - o1.size());
        }));
        return currentStandings;
    }

    public static void printElectionStandings(String election) {
        sWriter.println("Standings of election " + election);
        for (Map.Entry<String, ArrayList<Ballot>> candidate : getSortedStandings(election)) {
            String line = "    " + candidate.getValue().size() + ": " + candidate.getKey();
            sWriter.print(line);
            for (int i = 0; i < 40 - line.length(); i++) {
                sWriter.print(" ");
            }
            sWriter.print("|");
            int chunkVotes = candidate.getValue().size() * 20 / getNumberOfVotesInElection(election);
            for (int i = 0; i < chunkVotes; i++) {
                if (i >= 10)
                    sWriter.print("*");
                else
                    sWriter.print("#");
            }
            for (int i = 0; i < 20 - chunkVotes; i++) {
                sWriter.print(" ");
            }
            sWriter.println("|");
        }
    }

    public static int getNumberOfVotesInElection(String election) {
        TreeMap<String, ArrayList<Ballot>> votes = sAllVotes.get(election);
        int numTotalVotes = 0;
        for (Map.Entry<String, ArrayList<Ballot>> candidates : votes.entrySet()) {
            numTotalVotes += candidates.getValue().size();
        }
        return numTotalVotes;
    }

    public static SimpleEntry<String, Integer> getCurrentLeader(String election) {
        TreeMap<String, ArrayList<Ballot>> votes = sAllVotes.get(election);
        String potentialCurrentLeader = null;
        Integer potentialLeaderVotes = 0;
        for (Map.Entry<String, ArrayList<Ballot>> candidates : votes.entrySet()) {
            Integer votesForThisCandidate = candidates.getValue().size();
            if (votesForThisCandidate.equals(potentialLeaderVotes)) {
                potentialCurrentLeader = null;// tied in the lead
            } else if (votesForThisCandidate > potentialLeaderVotes) {
                potentialCurrentLeader = candidates.getKey();
                potentialLeaderVotes = votesForThisCandidate;
            }
        }
        return new SimpleEntry<>(potentialCurrentLeader, potentialLeaderVotes);
    }

    public static int getNextWorstNumberOfVotes(String election) {
        ArrayList<Map.Entry<String, ArrayList<Ballot>>> currentStandings = getSortedStandings(election);
        int minVotes = currentStandings.get(currentStandings.size() - 1).getValue().size();
        if (currentStandings.size() < 2) {
            return 1;
        }
        for (int i = currentStandings.size() - 2; i >= 0; i--) {
            int thisNumVotes = currentStandings.get(i).getValue().size();
            if (thisNumVotes > minVotes) {
                return thisNumVotes;
            }
        }
        return 1;
    }

    public static SimpleEntry<String, Integer> getCurrentLoser(String election) {
        TreeMap<String, ArrayList<Ballot>> votes = sAllVotes.get(election);
        String potentialCurrentLoser = null;
        Integer potentialLoserVotes = 9999;
        boolean tie = false;
        for (Map.Entry<String, ArrayList<Ballot>> candidates : votes.entrySet()) {
            Integer votesForThisCandidate = candidates.getValue().size();
            if (votesForThisCandidate.equals(potentialLoserVotes)) {
                tie = true;
                // tied in the loss, the first randomly chosen person is still the loser
            } else if (votesForThisCandidate < potentialLoserVotes) {
                potentialCurrentLoser = candidates.getKey();
                potentialLoserVotes = votesForThisCandidate;
                tie = false;
            }
        }
        if (tie) {
            if (potentialLoserVotes * 2 > getNextWorstNumberOfVotes(election)) {
                // just eliminate first tied candidate potentialCurrentLoser=null;//no one can
                // be eliminated simply
            } else {
                sWriter.println("Two candidates tied in the loss of election " + election
                        + " but neither are important. Candidate " + potentialCurrentLoser + " will be eliminated.");
            }
        }

        return new SimpleEntry<>(potentialCurrentLoser, potentialLoserVotes);
    }

    public static void registerCandidate(String electionName, String candidate) {
        TreeMap<String, ArrayList<Ballot>> candidatesForThisElection = sAllVotes.get(electionName);
        if (!candidatesForThisElection.containsKey(candidate)) {// candidate not in list for this election
            candidatesForThisElection.put(candidate, new ArrayList<>());
        }
    }

    public static void deregisterCandidate(String electionName, String candidate) throws Exception {
        TreeMap<String, ArrayList<Ballot>> candidatesVotesForThisElection = sAllVotes.get(electionName);
        if (candidatesVotesForThisElection.get(candidate).size() > 0) {
            throw new Exception("Trying to deregister candidate " + candidate + " in election " + electionName
                    + " but they have votes!");
        }
    }

    public static boolean isCandidateAvailable(String electionName, String candidate) {
        if (!App.sAllVotes.get(electionName).containsKey(candidate)) {
            return false;
        }

        return true;
    }

}