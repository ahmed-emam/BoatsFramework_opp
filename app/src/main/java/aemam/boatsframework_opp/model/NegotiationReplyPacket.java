package aemam.boatsframework_opp.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by aemam on 12/27/15.
 */

/*                          Negotiation reply packet format
 * --------------------------------------------------------------------------------
 * |                 Bundle ID 1       |   Status of bundle   |       offset      |
 * --------------------------------------------------------------------------------
 * |                                   ......                                     |
 * --------------------------------------------------------------------------------
 */
public class NegotiationReplyPacket implements Serializable{
    public final byte DONTSEND = 0;
    public final byte SEND = 1;
    public final byte SENDMETADATA = 2;

    ArrayList<NegotiationReplyEntry> negotiationReplyEntries;


    public NegotiationReplyPacket(){
        this.negotiationReplyEntries = new ArrayList<>();

    }
    public void addEntry(NegotiationReplyEntry negotiationReplyEntry){
//        this.bundleIds.add(filename);
//        this.sendIt.add(sendIt);
        this.negotiationReplyEntries.add(negotiationReplyEntry);
    }

//    public ArrayList<String> getBundleIds() {
//        return bundleIds;
//    }
//
//    public ArrayList<Byte> getSendIt() {
//        return sendIt;
//    }


    public ArrayList<NegotiationReplyEntry> getNegotiationReplyEntries() {
        return negotiationReplyEntries;
    }

    public static class NegotiationReplyEntry implements Serializable{
        String bundleId;
        /**
         * 0 -> don't send it
         * 1 -> Send it
         * 2 -> send meta-data only
         */
        byte status;
        long offset;
        public NegotiationReplyEntry(String bundleId, byte status, long offset){
            this.bundleId = bundleId;
            this.status = status;
            this.offset = offset;
        }

        public String getBundleId() {
            return bundleId;
        }

        public byte getStatus() {
            return status;
        }

        public long getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return this.bundleId+"|"+this.status+"|"+this.offset;
        }
    }
}
