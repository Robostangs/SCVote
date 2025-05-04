import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public final class Ballot {
    private TreeMap<String, Queue<String>> mVotes = new TreeMap<>();// ballotData
    private int mBallotIndex;

    public Ballot(String ballotLine, int idx) {
        mBallotIndex = idx;
        String[] ballotEntries = ballotLine.split(",");// chosenCandidates
        if (ballotEntries.length != App.columnHeadings.length) {
            System.err.println("Length of ballot " + ballotLine + " doesn't match number of elections!");
            System.exit(-1);
        }
        for (int columnIdx = 3; columnIdx < ballotEntries.length; columnIdx++) {// start at 3 to skip timestamp, email,
                                                                                // name
            String thisColumnHeading = App.columnHeadings[columnIdx];// thisColumnString
            String thisElectionName = App.getElectionNameFromHeading(thisColumnHeading);
            String thisChoice = ballotEntries[columnIdx];// chosenCandidate

            if (!mVotes.containsKey(thisElectionName)) {// no votes yet by this ballot for this election
                mVotes.put(thisElectionName, new LinkedList<>());
            }

            if (thisChoice.length() > 0) {
                App.registerCandidate(thisElectionName, thisChoice);
                mVotes.get(thisElectionName).add(thisChoice);// ballot must have numbered choices in order (choice 1 in
                                                             // column D, 2 in E, etc.)
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder rBuilder = new StringBuilder();
        for (Map.Entry<String, Queue<String>> thisElectionChoices : mVotes.entrySet()) {
            rBuilder.append("Election " + thisElectionChoices.getKey() + ":");
            List<String> listOfChoices = new ArrayList<>(thisElectionChoices.getValue());
            for (int i = 0; i < listOfChoices.size(); i++) {
                rBuilder.append(" #" + i + "," + listOfChoices.get(i) + "; ");
            }
        }
        return (rBuilder.toString());
    }

    public boolean cast(String election) throws IOException {
        if (mVotes.get(election).size() == 0) {
            App.writer.println(
                    "Ballot " + mBallotIndex + " thrown away for election " + election
                            + ". No more choices available.");
            return false;
        }

        String thisBallotsChoice = mVotes.get(election).peek();

        if (!App.isCandidateAvailable(election, thisBallotsChoice)) {
            mVotes.get(election).poll(); // like pop
            App.writer.println(
                    "Ballot " + mBallotIndex + " choice " + thisBallotsChoice
                            + " is captain or has already been eliminated. Trying next choice.");
            return cast(election); // now the choice should be valid
        }

        ArrayList<Ballot> thisCandidatesBallotList = App.allVotes.get(election).get(thisBallotsChoice);

        if (thisCandidatesBallotList.contains(this)) {
            System.err.println(
                    "Ballot " + mBallotIndex + " already voted for " + thisBallotsChoice + " in election " + election);
            System.exit(-2);
        }
        thisCandidatesBallotList.add(this);
        App.writer.println("Ballot " + mBallotIndex + " cast for " + thisBallotsChoice + " in election " + election);

        return true;
    }
}