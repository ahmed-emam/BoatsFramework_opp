package aemam.boatsframework_opp.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by aemam on 12/27/15.
 */

/*                          Negotiation reply packet format
 * --------------------------------------------------------------------------------
 * | Should send |                      Bundle ID 1                               |
 * --------------------------------------------------------------------------------
 * |                                   ......                                     |
 * --------------------------------------------------------------------------------
 */
public class NegotiationReplyPacket implements Serializable{

    ArrayList<String> bundleIds;
    /**
     * 0 -> don't send it
     * 1 -> Send it
     * 2 -> send meta-data only
     */
    ArrayList<Byte> sendIt;
    public NegotiationReplyPacket(){
        this.bundleIds = new ArrayList<>();
        this.sendIt = new ArrayList<>();
    }
    public void addBundle(String filename, Byte sendIt){
        this.bundleIds.add(filename);
        this.sendIt.add(sendIt);
    }

    public ArrayList<String> getBundleIds() {
        return bundleIds;
    }

    public ArrayList<Byte> getSendIt() {
        return sendIt;
    }

    public class NegotiationReplyEntry implements Serializable{
        String bundleId;
        byte status;
        int offset;
        public NegotiationReplyEntry(String bundleId, byte status, int offset){
            this.bundleId = bundleId;
            this.status = status;
            this.offset = offset;
        }

    }
}
