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

    private static TreeMap<Integer, Ballot> ballots = new TreeMap<>();
    public static TreeMap<String, TreeMap<String, ArrayList<Ballot>>> allVotes = new TreeMap<>();// first string is
                                                                                                 // election, second is
                                                                                                 // candidate
    public static ArrayList<String> importantElections = new ArrayList<>();
    public static TreeMap<String, String> winners = new TreeMap<>();
    public static String[] columnHeadings;
    public static PrintWriter writer;

    public static void main(String[] args) throws Exception {
        System.out.println("Looking in " + System.getProperty("user.dir")+" for voteTallies.csv");
        BufferedReader reader;
        
        try {
            reader = new BufferedReader(new FileReader("voteTallies.csv"));
            writer = new PrintWriter(new FileWriter("voteResults.txt"),true);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        String line;
        int lineIdx=1;
        while((line = reader.readLine())!=null){
            line=line.replace("\"","");//google sheets adds a bunch of random quotes

            if(line.contains("Timestamp")){
                writer.println("Processing Headings");
                columnHeadings = line.split(",");
                for (String heading : Arrays.asList(columnHeadings)) {
                    if(heading.contains("Timestamp")||heading.contains("Email Address")||heading.contains("Full Name")){
                        continue;
                    }
                    String election = getElectionNameFromHeading(heading);//remove first/second/third
                    writer.println("Found column "+heading);

                    if(!allVotes.containsKey(election)){
                        allVotes.put(election, new TreeMap<>());
                        writer.println("Created election "+election);
                        if(election.contains("Captain")){
                            importantElections.add(election);
                            writer.println("Important election "+election+" noted");
                        }
                    }
                }
                writer.println("Elections: "+allVotes.keySet().toString());
                writer.println("Important elections: "+importantElections.toString());
            }
            else{
                ballots.put(lineIdx, new Ballot(line,lineIdx));
                writer.println("Found ballot "+line+". Choices: "+ballots.get(lineIdx).toString());
                lineIdx++;
            }
            
        }

        for(String importantName:importantElections){
            runElection(importantName);
            System.out.println("Running election "+importantName);
            if (winners.containsKey(importantName)) {  // a winner has been found so they need to be removed from the list of eligible candidates
                allVotes.keySet().forEach((electionName) -> {deregisterCandidate(electionName, winners.get(importantName));});
                
            }
        }

        for(Map.Entry<String,TreeMap<String,ArrayList<Ballot>>> elections:allVotes.entrySet()){
            if(!importantElections.contains(elections.getKey())){
                System.out.println("Running election "+elections.getKey());
                runElection(elections.getKey());
            }
        }
        
        writer.println("Results:");
        winners.forEach((election,winner)->{
            writer.println("    "+election+": "+winner);
        });


        writer.flush();
        System.out.println("Done");
    }

    public static String getElectionNameFromHeading(String heading) {
        return (heading.replaceAll(" \\[.*", ""));
    }

    public static void runElection(String election) throws IOException {
        writer.println("Running election " + election);

        ballots.forEach((idx, ballot) -> {
            try {
                ballot.cast(election);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        writer.println("Initial casting complete");
        printElectionStandings(election);

        for (int round = 0; round <= 10; round++) {
            int numVotes = getNumberOfVotesInElection(election);
            writer.println(numVotes + " votes in election " + election);
            SimpleEntry<String, Integer> currentLeader = getCurrentLeader(election);
            if (currentLeader.getValue() > numVotes / 2 && currentLeader.getKey() != null) {
                writer.println("Winner found. Winner is " + currentLeader.getKey() + " with " + currentLeader.getValue()
                        + " votes.");
                winners.put(election, currentLeader.getKey());
                break;
            }
            int numberOfCandidatesBeforeRemoval = allVotes.get(election).size();
            SimpleEntry<String, Integer> currentLoser = getCurrentLoser(election);
            if (currentLoser.getKey() == null) {
                writer.println("Unbroken tie for the lose. See standings and resolve manually.");
                break;
            }
            ArrayList<Ballot> votesForLoser = allVotes.get(election).get(currentLoser.getKey());
            allVotes.get(election).remove(currentLoser.getKey());
            writer.println("Candidate " + currentLoser.getKey() + " removed.");

            for (Ballot ballotForLoser : votesForLoser) { // now they are eliminated remove those that voted for them
                ballotForLoser.cast(election);
            }

            if (allVotes.get(election).size() >= numberOfCandidatesBeforeRemoval) {
                System.err.println("Recast all votes for " + currentLoser + " but now there are more candidates");
                System.exit(-5);
            }
            writer.println("Starting round " + (round + 2));
            printElectionStandings(election);
        }

        writer.println("Finished election " + election);
    }

    public static ArrayList<Map.Entry<String, ArrayList<Ballot>>> getSortedStandings(String election) {
        ArrayList<Map.Entry<String, ArrayList<Ballot>>> currentStandings = new ArrayList<>(
                allVotes.get(election).entrySet());
        currentStandings.sort(Entry.comparingByValue((ArrayList<Ballot> o1, ArrayList<Ballot> o2) -> {
            return (int) Math.signum(o2.size() - o1.size());
        }));
        return currentStandings;
    }

    public static void printElectionStandings(String election) {
        writer.println("Standings of election " + election);
        for (Map.Entry<String, ArrayList<Ballot>> candidate : getSortedStandings(election)) {
            String line = "    " + candidate.getValue().size() + ": " + candidate.getKey();
            writer.print(line);
            for (int i = 0; i < 40 - line.length(); i++) {
                writer.print(" ");
            }
            writer.print("|");
            int chunkVotes = candidate.getValue().size() * 20 / getNumberOfVotesInElection(election);
            for (int i = 0; i < chunkVotes; i++) {
                if (i >= 10)
                    writer.print("*");
                else
                    writer.print("#");
            }
            for (int i = 0; i < 20 - chunkVotes; i++) {
                writer.print(" ");
            }
            writer.println("|");
        }
    }

    public static int getNumberOfVotesInElection(String election) {
        TreeMap<String, ArrayList<Ballot>> votes = allVotes.get(election);
        int numTotalVotes = 0;
        for (Map.Entry<String, ArrayList<Ballot>> candidates : votes.entrySet()) {
            numTotalVotes += candidates.getValue().size();
        }
        return numTotalVotes;
    }

    public static SimpleEntry<String, Integer> getCurrentLeader(String election) {
        TreeMap<String, ArrayList<Ballot>> votes = allVotes.get(election);
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
        TreeMap<String, ArrayList<Ballot>> votes = allVotes.get(election);
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
                writer.println("Two candidates tied in the loss of election " + election
                        + " but neither are important. Candidate " + potentialCurrentLoser + " will be eliminated.");
            }
        }

        return new SimpleEntry<>(potentialCurrentLoser, potentialLoserVotes);
    }

    public static void registerCandidate(String electionName, String candidate) {
        TreeMap<String, ArrayList<Ballot>> candidatesForThisElection = allVotes.get(electionName);
        if (!candidatesForThisElection.containsKey(candidate)) {// candidate not in list for this election
            candidatesForThisElection.put(candidate, new ArrayList<>());
        }
    }

    public static void deregisterCandidate(String electionName, String candidate) throws Exception {
        TreeMap<String, ArrayList<Ballot>> candidatesVotesForThisElection = allVotes.get(electionName);
        if (candidatesVotesForThisElection.get(candidate).size() > 0) {
            throw new Exception("Trying to deregister candidate " + candidate + " in election " + electionName
                    + " but they have votes!");
        }
    }

    public static boolean isCandidateAvailable(String electionName, String candidate) {
        if (!App.allVotes.get(electionName).containsKey(candidate)) {
            return false;
        }

        return true;
    }

}