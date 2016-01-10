package aemam.boatsframework_opp.model;

/**
 * Created by aemam on 12/27/15.
 */

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A class that represent an opportunistic bundle exchange between nodes
 */
public class Bundle implements Serializable{
    private volatile int myID = 0;
    private int source = 0;         //Node that produced the bundle
    private int destination = 0;    //Node that should receive the bundle
    private String bundleID;        //Data of the bundle
    private long bundleSize;
    /**
     * For trajectory
     */
    private ArrayList<Integer> nodes;  //Intermediate nodes that this bundle went through
    private ArrayList<Long> disconnectTime;
    private ArrayList<Long> transferTime;  //Data transfer duration at each intermediate node
    private ArrayList<Long> connectionEstablishment;    //Connection establishment delay at each
    //intermediate nodes
    private ArrayList<Long> waitingDelay;   //Delay that represent
    private volatile long start_recv_time;   //TimeStamp of when I received the first data packet of the file
    private volatile long end_recv_time;     //TimeStamp of when I received the last data packet of the file
    private volatile long bytesReceived;

    private String checkSum;        //CheckSum of the file to check for successful data transfer
    private boolean isDelivered;

    private long disconnect_time_start;
    private long disconnect_time_end;

    private long connect_time_start;
    private long connect_time_end;

    private long scan_time_start;
    private long scan_time_end;


    public String getBundleID(){
        return bundleID;
    }
    public int getSource() {
        return source;
    }
    public int getDestination() {
        return destination;
    }
    public ArrayList<Integer> getNodes() {
        return nodes;
    }
    public ArrayList<Long> getConnectionEstablishment() {
        return connectionEstablishment;
    }
    public ArrayList<Long> getDisconnectTime() {
        return disconnectTime;
    }
    public String getCheckSum() {
        return checkSum;
    }
    public ArrayList<Long> getWaitingDelay() {
        return waitingDelay;
    }
    public ArrayList<Long> getTransferTime() {
        return transferTime;
    }
    public long getStart_recv_time() {
        return start_recv_time;
    }
    public long getEnd_recv_time() {
        return end_recv_time;
    }
    public long getDisconnect_time_start() {
        return disconnect_time_start;
    }
    public long getDisconnect_time_end() {
        return disconnect_time_end;
    }
    public long getConnect_time_start() {
        return connect_time_start;
    }
    public long getConnect_time_end() {
        return connect_time_end;
    }
    public long getScan_time_start() {
        return scan_time_start;
    }
    public long getScan_time_end() {
        return scan_time_end;
    }
    public long getBytesReceived() {
        return bytesReceived;
    }
    public long getBundleSize() {
        return bundleSize;
    }

    /**
     *
     * @param sourceNodeID
     * @param destinationNodeID
     * @param filename
     */
    public Bundle(int sourceNodeID,int destinationNodeID, String filename, int id){
        this.myID = id;
        source = sourceNodeID;
        destination = destinationNodeID;
        bundleID = filename;
        nodes = new ArrayList<>();
        transferTime = new ArrayList<>();
        connectionEstablishment = new ArrayList<>();
        waitingDelay = new ArrayList<>();
        disconnectTime = new ArrayList<>();
        start_recv_time = -1;
        end_recv_time = -1;
        bytesReceived = 0;
        checkSum = "";
        isDelivered = false;
    }

    public Bundle(String filename, int id){
        this.myID = id;
        bundleID = filename;
        source = -1;
        destination = -1;
        nodes = new ArrayList<>();
        transferTime = new ArrayList<>();
        connectionEstablishment = new ArrayList<>();
        waitingDelay = new ArrayList<>();
        disconnectTime = new ArrayList<>();
        start_recv_time = -1;
        end_recv_time = -1;
        bytesReceived = 0;
        checkSum = "";
        isDelivered = false;
    }

    public void addDisconnectTimeStamp(long disconnect) { disconnectTime.add(disconnect);}
    public void addNode(int nodeID){
        nodes.add(nodeID);
    }
    public void addTransferTimeStamp(long duration){
        transferTime.add(duration);
    }
    public void addConnectTimeStamp(long duration){
        connectionEstablishment.add(duration);
    }
    public void addWaitingTimeStamp(long duration){ waitingDelay.add(duration); }


    public void setNodes(ArrayList<Integer> nodes) {
        this.nodes = nodes;
    }
    public void setDisconnectTime(ArrayList<Long> disconnectTime) {
        this.disconnectTime = disconnectTime;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public void setBundleSize(long bundleSize) {
        this.bundleSize = bundleSize;
    }

    public void setTransferTime(ArrayList<Long> transferTime) {
        this.transferTime = transferTime;
    }
    public void setConnectionEstablishment(ArrayList<Long> connectionEstablishment) {
        this.connectionEstablishment = connectionEstablishment;
    }
    public void setWaitingDelay(ArrayList<Long> waitingDelay) {
        this.waitingDelay = waitingDelay;
    }
    public void set_end_recv_time(long recv_time){
        this.end_recv_time = recv_time;
    }
    public void set_start_recv_time(long recv_time){
        this.start_recv_time = recv_time;
    }
    public void set_checkSum(String checkSum){
        this.checkSum = checkSum;
    }
    public void setDestination(int destination) {
        this.destination = destination;
    }
    public void setSource(int source) {
        this.source = source;
    }
    public void setDisconnect_time_start(long disconnect_time_start) {
        this.disconnect_time_start = disconnect_time_start;
    }
    public void setDisconnect_time_end(long disconnect_time_end) {
        this.disconnect_time_end = disconnect_time_end;
    }
    public void setConnect_time_start(long connect_time_start) {
        this.connect_time_start = connect_time_start;
    }
    public void setConnect_time_end(long connect_time_end) {
        this.connect_time_end = connect_time_end;
    }
    public void setScan_time_start(long scan_time_start) {
        this.scan_time_start = scan_time_start;
    }
    public void setScan_time_end(long scan_time_end) {
        this.scan_time_end = scan_time_end;
    }
    public void setTransferTimeStamp(long end){
        for(int i = 0; i < nodes.size(); i++){
            if(nodes.get(i) == this.myID){
                /**
                 * Node 1 transfer time stamps are at index 2 and 3
                 *
                 */
                int index = i*2;

                transferTime.set(index+1, end);
//                long oldTime = transferTime.get(i);
//                MainActivity.debug("Old duration "+oldTime);
//                transferTime.set(i, (oldTime + duration));
//                MainActivity.debug("New duration "+transferTime.get(i));
            }
        }
    }

    public void delivered(){
        isDelivered = true;
    }

    public boolean isDelivered(){
        return isDelivered;
    }

    public String toString(){
        String bundle = "======Opp_Bundle======";
        bundle += "\nDelivered:"+ (this.isDelivered? "Yes":"No");
        bundle += "\nSource:"+source;
        bundle += "\nDestination:"+destination;
        bundle += "\nFile name: "+ bundleID;
        bundle += "\nNodes:\t";
        for(int i = 0; i < nodes.size(); i++)
            bundle += nodes.get(i)+"\t";


        bundle += "\nDisconnect Time:\t";
        for(int i = 0; i < disconnectTime.size(); i++)
            bundle += disconnectTime.get(i)+"\t";

        bundle += "\nTransfer Time:\t";
        for(int i = 0; i < transferTime.size(); i++)
            bundle += transferTime.get(i)+"\t";

        bundle += "\nConnect Delay:\t";
        for(int i = 0; i < connectionEstablishment.size(); i++)
            bundle += connectionEstablishment.get(i)+"\t";

        bundle += "\nScan Delay:\t";
        for(int i = 0; i < waitingDelay.size(); i++)
            bundle += waitingDelay.get(i)+"\t";

        bundle += "\nDisconnect Delay:\t"+this.disconnect_time_start+"\t"+this.disconnect_time_end;
        bundle += "\nConnect Delay:\t"+this.connect_time_start+"\t"+this.connect_time_end;
        bundle += "\nScan Delay:\t"+this.scan_time_start+"\t"+this.scan_time_end;

        return bundle;
    }

    public boolean isEqual(Bundle other){
        if(this.source != other.source)
            return false;
        if(this.destination != other.destination)
            return false;
        return this.bundleID.equals(other.bundleID);

    }


}
