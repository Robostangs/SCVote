import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public final class Ballot {
    private TreeMap<String, ArrayList<String>> votes = new TreeMap<>();// ballotData
    private int ballotIndex;
    private TreeMap<String, Integer> currentChoiceIdxs = new TreeMap<>();

    public Ballot(String ballotLine, int idx) {
        ballotIndex = idx;
        String[] voteNames = ballotLine.split(",");// chosenCandidates
        if (voteNames.length != App.columnHeadings.length) {
            System.err.println("Length of ballot " + ballotLine + " doesn't match number of elections!");
            System.exit(-1);
        }
        for (int i = 3; i < voteNames.length; i++) {// start at 3 to skip timestamp, email, name
            String thisColumString = App.columnHeadings[i];// thisColumnString
            String thisElection = App.getElectionNameFromHeading(thisColumString);
            String thisVote = voteNames[i];// chosenCandidate

            if (!votes.containsKey(thisElection)) {// no votes yet by this ballot for this election
                votes.put(thisElection, new ArrayList<>());
            }

            if (thisVote.length() > 0) {
                TreeMap<String, ArrayList<Ballot>> candidatesForThisElection = App.allVotes.get(thisElection);
                if (!candidatesForThisElection.containsKey(thisVote)) {// candidate not in list for this election
                    candidatesForThisElection.put(thisVote, new ArrayList<>());
                }
                ArrayList<String> thisBallotVotesForThisElection = votes.get(thisElection);
                thisBallotVotesForThisElection.add(thisVote);// better be in order
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder rBuilder = new StringBuilder();
        for (Map.Entry<String, ArrayList<String>> thisElectionChoices : votes.entrySet()) {
            rBuilder.append("Election " + thisElectionChoices.getKey() + ":");
            for (int i = 0; i < thisElectionChoices.getValue().size(); i++) {
                rBuilder.append(" #" + i + "," + thisElectionChoices.getValue().get(i) + "; ");
            }
        }
        return (rBuilder.toString());
    }

    public boolean cast(String election) throws IOException {
        if (!currentChoiceIdxs.containsKey(election)) {
            currentChoiceIdxs.put(election, -1);
        }
        int choice = currentChoiceIdxs.get(election) + 1;

        if (votes.get(election).size() <= choice) {
            App.writer.println(
                    "Ballot " + ballotIndex + " thrown away for election " + election + ". Choice " + (choice + 1)
                            + " not available.");
            return false;
        }

        String thisBallotsChoice = votes.get(election).get(choice);

        if (!App.allVotes.get(election).containsKey(thisBallotsChoice)) {
            votes.get(election).remove(choice);
            App.writer.println(
                    "Ballot " + ballotIndex + " choice #" + (choice + 1)
                            + " was already eliminated. Moving choices up 1.");
            return cast(election); // now the choice should be valid
        }

        ArrayList<Ballot> thisCandidatesBallotList = App.allVotes.get(election).get(thisBallotsChoice);

        for (String importantElection : App.importantElections) {
            if (App.winners.containsKey(importantElection)) {
                if (App.winners.get(importantElection).equals(thisBallotsChoice)) {
                    // this ballot's choice is for someone who already won captain
                    votes.get(election).remove(choice);
                    App.writer.println(
                            "Ballot " + ballotIndex + " choice #" + (choice + 1) + " is captain. Moving choices up 1.");
                    return cast(election); // now the choice should be valid
                }
            }
        }

        if (thisCandidatesBallotList.contains(this)) {
            System.err.println(
                    "Ballot " + ballotIndex + " already voted for " + thisBallotsChoice + " in election " + election);
            System.exit(-2);
        }
        thisCandidatesBallotList.add(this);
        App.writer.println("Ballot " + ballotIndex + " cast for " + thisBallotsChoice + ", choice #" + (choice + 1)
                + " in election " + election);
        currentChoiceIdxs.put(election, choice);

        return true;
    }
}