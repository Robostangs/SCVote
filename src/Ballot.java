import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public final class Ballot {
    private TreeMap<String, ArrayList<String>> mVotes = new TreeMap<>();// ballotData
    private int mBallotIndex;
    private TreeMap<String, Integer> currentChoiceIdxs = new TreeMap<>();

    public Ballot(String ballotLine, int idx) {
        mBallotIndex = idx;
        String[] voteNames = ballotLine.split(",");// chosenCandidates
        if (voteNames.length != App.sColumnHeadings.length) {
            System.err.println("Length of ballot " + ballotLine + " doesn't match number of elections!");
            System.exit(-1);
        }
        for (int columnIdx = 3; columnIdx < voteNames.length; columnIdx++) {// start at 3 to skip timestamp, email, name
            String thisColumnHeading = App.sColumnHeadings[columnIdx];// thisColumnString
            String thisElection = App.getElectionNameFromHeading(thisColumnHeading);
            String thisVote = voteNames[columnIdx];// chosenCandidate

            if (!mVotes.containsKey(thisElection)) {// no votes yet by this ballot for this election
                mVotes.put(thisElection, new ArrayList<>());
            }

            if (thisVote.length() > 0) {
                TreeMap<String, ArrayList<Ballot>> candidatesForThisElection = App.sAllVotes.get(thisElection);
                if (!candidatesForThisElection.containsKey(thisVote)) {// candidate not in list for this election
                    candidatesForThisElection.put(thisVote, new ArrayList<>());
                }
                ArrayList<String> thisBallotVotesForThisElection = mVotes.get(thisElection);
                thisBallotVotesForThisElection.add(thisVote);// better be in order
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder rBuilder = new StringBuilder();
        for (Map.Entry<String, ArrayList<String>> thisElectionChoices : mVotes.entrySet()) {
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

        if (mVotes.get(election).size() <= choice) {
            App.sWriter.println(
                    "Ballot " + mBallotIndex + " thrown away for election " + election + ". Choice " + (choice + 1)
                            + " not available.");
            return false;
        }

        String thisBallotsChoice = mVotes.get(election).get(choice);

        if (!App.sAllVotes.get(election).containsKey(thisBallotsChoice)) {
            mVotes.get(election).remove(choice);
            App.sWriter.println(
                    "Ballot " + mBallotIndex + " choice #" + (choice + 1)
                            + " was already eliminated. Moving choices up 1.");
            return cast(election); // now the choice should be valid
        }

        ArrayList<Ballot> thisCandidatesBallotList = App.sAllVotes.get(election).get(thisBallotsChoice);

        for (String importantElection : App.sImportantElections) {
            if (App.sWinners.containsKey(importantElection)) {
                if (App.sWinners.get(importantElection).equals(thisBallotsChoice)) {
                    // this ballot's choice is for someone who already won captain
                    mVotes.get(election).remove(choice);
                    App.sWriter.println(
                            "Ballot " + mBallotIndex + " choice #" + (choice + 1) + " is captain. Moving choices up 1.");
                    return cast(election); // now the choice should be valid
                }
            }
        }

        if (thisCandidatesBallotList.contains(this)) {
            System.err.println(
                    "Ballot " + mBallotIndex + " already voted for " + thisBallotsChoice + " in election " + election);
            System.exit(-2);
        }
        thisCandidatesBallotList.add(this);
        App.sWriter.println("Ballot " + mBallotIndex + " cast for " + thisBallotsChoice + ", choice #" + (choice + 1)
                + " in election " + election);
        currentChoiceIdxs.put(election, choice);

        return true;
    }
}