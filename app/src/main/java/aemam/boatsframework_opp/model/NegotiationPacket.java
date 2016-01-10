package aemam.boatsframework_opp.model;

import java.io.Serializable;
import java.util.ArrayList;


    /*
     *                Opportunistic Negotiation packet format
     * --------------------------------------------------------------------
     * |Packet Length (int)|Source Node ID (int)|Destination Node ID (int)|
     * --------------------------------------------------------------------
     * | isDelivered |              Bundle ID 1                           |
     * --------------------------------------------------------------------
     * | isDelivered |              Bundle ...                            |
     * --------------------------------------------------------------------
     */

/**
 * Created by aemam on 12/27/15.
 */
public class NegotiationPacket implements Serializable{
    ArrayList<String> bundleIds;
    ArrayList<Boolean> delivered;

    public NegotiationPacket(){
        this.bundleIds = new ArrayList<>();
        this.delivered = new ArrayList<>();
    }

    public void addBundle(String filename, boolean delivered){
        this.delivered.add(delivered);
        this.bundleIds.add(filename);
    }

    public ArrayList<String> getBundleIds() {
        return bundleIds;
    }

    public ArrayList<Boolean> getDelivered() {
        return delivered;
    }
}
