package aemam.boatsframework_opp;

import android.text.style.DynamicDrawableSpan;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StreamCorruptedException;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import aemam.boatsframework_opp.model.Bundle;
import aemam.boatsframework_opp.model.DataPacket;
import aemam.boatsframework_opp.model.NegotiationPacket;
import aemam.boatsframework_opp.model.NegotiationReplyPacket;
import aemam.boatsframework_opp.model.NegotiationReplyPacket.NegotiationReplyEntry;
import aemam.boatsframework_opp.model.Packet;

/**
 * Created by aemam on 12/27/15.
 *
 * Consumes messages from the queue "buffer" and implement logic for device to device data transfer
 *
 **/

/**
 * Version 2.0 Dev started on 1/3/16
 * This version supports bundle transfer resume.
 * If the bundle wasn't successfully sent, the peers will resume bundle transfer next time they meet,
 * or if they met another peer that owns the same bundle
 */



/**
 * TODO: Solve corner case, if connection terminated right after meta-data was exchanged
 */

public class WorkerThread extends Thread {
    /*****************************
     *    DIFFERENT log types
     * ***************************
     */
    private static final int DEBUG = 0;
    private static final int WARN = 1;
    private static final int INFO = 2;
    private static final int ERROR = 3;
    /*****************************
     *    DIFFERENT log types
     * ***************************
     */

    /**************************************
     *             Packet Types
     * *************************************
     */
    private static final byte ADVERTISEMENT = 0;
    private static final byte HELLO = 1;
    private static final byte RTT = 2;
    private static final byte DATA = 3;
    private static final byte RTT_ACK = 5;
    private static final byte OPP_NEG = 6;              //Bundle Negotiation
    private static final byte OPP_FILE_ACK = 7;         //Bundle ACK
    private static final byte OPP_NEG_RPLY = 8;        //Bundle negotiation reply
    private static final byte OPP_META_DATA = 9;        //Bundle Meta Data
    private static final byte TERMINATE = 10;    //Terminate connection
    /**************************************
     *             Packet Types
     * *************************************
     */


    /******************************************
     *       TimeStamps for Trajectory
     ******************************************
     */
    private long disconnectDelay = 0L;
    private long connectionDelay = 0L;
    private long waitingDelay = 0L;
    /******************************************
     *       TimeStamps for Trajectory
     ******************************************
     */

    private static final String TAG = "Connection_Manager";

    OutputStream outputStream;

    int device_Id = 0;
    boolean threadIsAlive = true;
    BlockingQueue<Packet> incomingPackets;
    //    ReadingThread readingThread;

    long t_initial_start = 0;
    long t_initial_end = 0;
    long t_initial = 0;

    long t_receive_start = 0;
    long t_receive_end = 0;
    long t_receive = 0;

    long connectionEstablishment = 0L;
    MainActivity mainActivity;
    //    Queue<Bundle> filesToSend;
    Queue<NegotiationReplyEntry> filesToSend;
    Queue<String> filesToReceive;

    boolean gotNegotiation = false;
    boolean negotiated = false;

    private void log(String message) {
        mainActivity.log(message);
    }
    public void debug(String msg, int type){
        switch (type){
            case DEBUG:
                Log.d(TAG, msg);
                break;
            case WARN:
                Log.w(TAG, msg);
                break;
            case INFO:
                Log.i(TAG, msg);
                break;
            case ERROR:
                Log.e(TAG, msg);
                break;
        }
    }

    public WorkerThread( OutputStream outputStream, BlockingQueue<Packet> buf,
                         int device_Id,
                         MainActivity mainActivity){
        this.outputStream = outputStream;

        this.device_Id = device_Id;
        this.incomingPackets = buf;
        this.mainActivity = mainActivity;

        this.filesToSend = new LinkedList<>();
        this.filesToReceive = new LinkedList<>();
    }

    @Override
    public void run() {
        mainActivity.connection_establishment_time_end = System.currentTimeMillis();
        super.run();

        //Keep looping until I mark the thread DEAD and I have consumed all incoming packets
        while(threadIsAlive || incomingPackets.size() > 0) {

            if (device_Id != 0 && !negotiated)
                start_opportunistic_negotiation();

            try {
                Packet packet = incomingPackets.take();
//                debug(packet.toString(), INFO);

                if (mainActivity.DEVICE_ID == packet.getDestId()) {
                    byte packetType = packet.getType();
                    //Switch on Packet type
                    switch (packetType) {
                        case DATA:
                            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            DataPacket dataPacket = (DataPacket) objectInputStream.readObject();
//                            debug(dataPacket.toString(), INFO);

                            String fileName = dataPacket.getFilename();
                            File f = new File((MainActivity.rootDir + dataPacket.getFilename()));
                            if(!f.exists()){
                                log("Receiving file:" + fileName);
                                debug("Receiving  "+fileName, INFO);
//                                Bundle theBundle = mainActivity.getBundle(fileName);
//                                debug(theBundle.toString(), DEBUG);
//                                theBundle.set_start_recv_time(System.currentTimeMillis());
                            }

                            FileOutputStream fileOutputStream = new FileOutputStream(f, true);


                            fileOutputStream.write(dataPacket.getData(), 0, packet.getLength());
                            fileOutputStream.close();

                            Bundle theBundle = mainActivity.getBundle(dataPacket.getFilename());
                            if(theBundle != null)
                                theBundle.setBytesReceived(theBundle.getBytesReceived()
                                        +packet.getLength());


                            //I received the whole file
                            if(f.length() == dataPacket.getFileLength()) {
                                t_receive_end = System.currentTimeMillis();
                                debug("Received "+fileName, INFO);
                                mainActivity.debug("Received "+fileName);
                                log("File " + fileName + " is received");

                                //If this is an opportunistic data packet
                                theBundle.set_end_recv_time(t_receive_end);

                                //Edit data transfer duration
                                long transferTime = theBundle.getEnd_recv_time() -
                                        theBundle.getStart_recv_time();
                                if((f.length() >= theBundle.getBundleSize()) &&
                                        theBundle.getDestination() == mainActivity.DEVICE_ID)
                                    theBundle.delivered();
                                debug("Transfer took " + transferTime, INFO);
//                                theBundle.setTransferTimeStamp(
//                                        theBundle.getEnd_recv_time());
                                theBundle.addTransferTimeStamp(t_receive_end);

                                //Remove this bundle from the bundles to be received queue
                                filesToReceive.remove(theBundle.getBundleID());

//                                mainActivity.Bundles_nodes_mapping.get(theBundle).add(device_Id);
//                                mainActivity.Bundles_repo.add(theBundle);
//                                mainActivity.Bundles_queue.add(theBundle);
//                                        /*
//                                         * Check for file sanity
//                                         */
//                                String checkSum = mainActivity.hashFile(fileName, "MD5");
//                                if (checkSum.equals(theBundle.getCheckSum())) {
//                                    debug("File was sent correctly", INFO);
//                                } else {
//                                    debug("File wasn't sent in the right manner", );
//                                }

                                //Send data successfully transferred ACK
                                send_opp_file_ack(fileName);
                                mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
                                mainActivity.Bundles_queue.add(theBundle);
                            }
                            break;

                        case OPP_NEG:
                            debug("[OPPORTUNISTIC] Got Negotiation", DEBUG);
                            objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            NegotiationPacket negotiationPacket = (NegotiationPacket) objectInputStream.readObject();
                            process_opportunistic_negotiation(negotiationPacket);

                            break;

                        case OPP_FILE_ACK:

                            fileName = new String(packet.payload, 0, packet.getLength());
                            debug("[OPPORTUNISTIC] Got ACK for "+fileName, DEBUG);

                            Bundle newBundle = mainActivity.getBundle(fileName);
                            if(newBundle.getDestination() == device_Id){
                                newBundle.delivered();
                                mainActivity.addBundleToQueue(newBundle);
                            }
                            filesToSend.poll();
                            mainActivity.addNodeToBundleNegotiationMapping(mainActivity.getBundle(fileName), device_Id);

                            sendOppFile();
                            break;

                        case OPP_NEG_RPLY:
                            debug("[OPPORTUNISTIC] Got Negotiation reply", DEBUG);
                            objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            NegotiationReplyPacket negotiationReplyPacket = (NegotiationReplyPacket) objectInputStream.readObject();

                            process_opportunistic_negotiation_reply(negotiationReplyPacket);

                            gotNegotiation = true;

                            break;

                        case OPP_META_DATA:
                            debug("[OPPORTUNISTIC] Got Negotiation meta-data", DEBUG);
                            objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            Bundle bundle = (Bundle) objectInputStream.readObject();

                            process_metadata(bundle);
                            break;

                        case TERMINATE:
                            debug("[GOT TERMINATE PACKET] Terminating Connection with "+device_Id, INFO);
                            cancel();
                            break;

                        default:
                            debug("Not a valid packet type " +packetType, DEBUG);
                            break;
                    }
                }
                else{

                }
                   /*
                    If I am done negotiating and I have sent and received all the bundles, terminate
                    this connection
                     */
                if(gotNegotiation && negotiated
                        && filesToReceive.size() == 0 && filesToSend.size() == 0){
                    debug("Thread job is done, kill the the thread",DEBUG);
                    cancel();
                }
            }
            catch(InterruptedException e){
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (OptionalDataException e) {
                e.printStackTrace();
            } catch (StreamCorruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(filesToReceive.size() > 0 || filesToSend.size() > 0) {
            for (String bundleID : filesToReceive) {
                Bundle theBundle = mainActivity.getBundle(bundleID);
                if (theBundle != null) {
                    File f = new File((MainActivity.rootDir + bundleID));
                    if (f.exists()) {
                        if (theBundle.getBytesReceived() > 0) {
                            long time_recv = System.currentTimeMillis();
                            theBundle.set_end_recv_time(time_recv);
                            theBundle.addTransferTimeStamp(time_recv);
                            mainActivity.Bundles_queue.add(theBundle);
                        } else {
                            removeTimeStamps(theBundle);
                        }
                    } else {
                        mainActivity.removeBundle(theBundle.getBundleID());
//                    mainActivity.
                    }
                }
            }

            for (NegotiationReplyEntry bundleID : filesToSend) {
                debug("[SENDING]" + bundleID.getBundleId() + " will be removed", INFO);

//            mainActivity.removeNodeToBundleNegotiationMapping(theBundle, device_Id);
            }
        }

        terminateConn();
    }
    private void removeTimeStamps(Bundle theBundle){
        ArrayList<Integer> nodes = theBundle.getNodes();
        nodes.remove(nodes.size()-1);

        ArrayList<Long> timeStamp = theBundle.getTransferTime();
        timeStamp.remove(timeStamp.size()-1);

        timeStamp = theBundle.getDisconnectTime();
        timeStamp.remove(timeStamp.size()-1);
        timeStamp.remove(timeStamp.size()-1);

        timeStamp = theBundle.getConnectionEstablishment();
        timeStamp.remove(timeStamp.size()-1);
        timeStamp.remove(timeStamp.size()-1);

        timeStamp = theBundle.getWaitingDelay();
        timeStamp.remove(timeStamp.size()-1);
        timeStamp.remove(timeStamp.size()-1);
    }

    /**
     * Terminating connection
     */
    public void terminateConn() {
        debug("Logging bundles", DEBUG);
        mainActivity.log_bundles();
        mainActivity.disconnect_time_start = System.currentTimeMillis();
        debug("Terminating connection", DEBUG);
        cancel();
        mainActivity.disconnect_time_end = System.currentTimeMillis();
    }


    public void start_opportunistic_negotiation(){
        NegotiationPacket negotiationPacket = new NegotiationPacket();
        debug("Negotiating " + mainActivity.Bundles_repo.size() + " bundles", INFO);

        //=================Filling up Negotiation Packet=================
        for (int i = 0; i < mainActivity.Bundles_repo.size(); i++) {
            Bundle thebundle = mainActivity.Bundles_repo.get(i);
            negotiationPacket.addBundle(thebundle.getBundleID(), thebundle.isDelivered());
        }
        //=================Filling up Negotiation Packet=================

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput output = new ObjectOutputStream(bos);
            output.writeObject(negotiationPacket);
            byte[] objectToArray = bos.toByteArray();

            Packet packet = new Packet(mainActivity.DEVICE_ID, device_Id, OPP_NEG,0, objectToArray);
            writeToNode(device_Id, packet);
            debug("Wrote negotiation packet to " + device_Id, INFO);
            negotiated = true;      //The negotiation packet is sent to the node
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Read the negotiation packet for opportunistic file transfer
     * This function checks my bundle repository and will ask in the negotiation reply for
     * bundles it doesn't have
     * @param packet    The negotiation packet received
     */
    private void process_opportunistic_negotiation(NegotiationPacket packet){
        ArrayList<String> bundleIds = packet.getBundleIds();
        ArrayList<Boolean> delivered = packet.getDelivered();
        NegotiationReplyPacket negotiationReplyPacket = new NegotiationReplyPacket();
        for(int i = 0; i < bundleIds.size(); i++){
            boolean isDelivered = delivered.get(i);
            String bundleId = bundleIds.get(i);
            NegotiationReplyEntry negotiationReplyEntry;
            Bundle theBundle =  mainActivity.getBundle(bundleId);

            if(isDelivered){

                if(theBundle != null){
                    if(theBundle.isDelivered()){
                        negotiationReplyEntry = new NegotiationReplyEntry
                                (bundleId, negotiationReplyPacket.DONTSEND, 0);
                        negotiationReplyPacket.addEntry(negotiationReplyEntry);
                    }
                    else{
                        negotiationReplyEntry = new NegotiationReplyEntry
                                (bundleId, negotiationReplyPacket.SENDMETADATA, 0);
                        negotiationReplyPacket.addEntry(negotiationReplyEntry);

                        debug("[process_opportunistic_negotiation]"+
                                (mainActivity.removeBundle(bundleId)? "Removed ":"Didn't remove ")
                                +bundleId, DEBUG);
                    }
                }
                else {
                    negotiationReplyEntry = new NegotiationReplyEntry
                            (bundleId, negotiationReplyPacket.SENDMETADATA, 0);
                    negotiationReplyPacket.addEntry(negotiationReplyEntry);
                }
            }
            else {
                //The bundle exists
                if (theBundle != null) {
                    File f = new File((MainActivity.rootDir + theBundle.getBundleID()));

                    //Sanity check if the file exists or not, it should always exist
                    if (f.exists()) {
                        //if I have the whole bundle and more....don't send me the bundle
                        if (f.length() >= theBundle.getBundleSize()) {
                            negotiationReplyEntry = new NegotiationReplyEntry
                                    (bundleId, negotiationReplyPacket.DONTSEND, 0);
                            negotiationReplyPacket.addEntry(negotiationReplyEntry);
                        }
                        //I don't have the full bundle, so send me what is left
                        else {
                            negotiationReplyEntry = new NegotiationReplyEntry
                                    (bundleId, negotiationReplyPacket.SEND, f.length());
                            negotiationReplyPacket.addEntry(negotiationReplyEntry);
                            debug("[process_opportunistic_negotiation] " +
                                    "Will receive "+bundleId+" from offset "+f.length(), DEBUG);
                            if(filesToReceive.size() == 0 ){
                                theBundle.set_start_recv_time(System.currentTimeMillis());
                                debug("[process_opportunistic_negotiation] " +
                                        bundleId+" is the first bundle", DEBUG);
                            }
                            filesToReceive.add(theBundle.getBundleID());
                        }
                    }
                    else{
                        debug("[process_opportunistic_negotiation]"+f.getName()+" should exist", ERROR);
                    }
                }
                else {
                    negotiationReplyEntry = new NegotiationReplyEntry
                            (bundleId, negotiationReplyPacket.SEND, 0);
                    negotiationReplyPacket.addEntry(negotiationReplyEntry);
                    debug("[process_opportunistic_negotiation] " +
                            "Will receive "+bundleId+" from the beginning", DEBUG);
                    filesToReceive.add(bundleId);
                }
            }

            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput output = new ObjectOutputStream(bos);

                output.writeObject(negotiationReplyPacket);

                byte[] objectToArray = bos.toByteArray();

                Packet outgoingPacket = new Packet(mainActivity.DEVICE_ID, device_Id, OPP_NEG_RPLY,0, objectToArray);
                writeToNode(device_Id, outgoingPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }


    /*                 opportunistic file ACK packet format
     * ----------------------------------------------------------------------------
     * | Opp file ack TAG BYTE (byte) | File name length (int) | file name (bytes)|
     * ----------------------------------------------------------------------------
     */
    /**
     * This function send an ack to the sender notifying him that the bundle with file name
     * "filename" has been successfully received and the sender can proceed by sending the
     * next bundle in his "ToSend" queue
     * @param fileName
     */
    private void send_opp_file_ack(String fileName){
        byte[] filenameInBytes = fileName.getBytes();
        Packet packet = new Packet(mainActivity.DEVICE_ID, device_Id, OPP_FILE_ACK,
                filenameInBytes.length, filenameInBytes);
//        if(!filesToReceive.isEmpty()){
//
//        }
        writeToNode(device_Id, packet);
    }


    private void process_opportunistic_negotiation_reply(NegotiationReplyPacket negotiationReplyPacket){

        ArrayList<NegotiationReplyEntry> negotiationReplyEntries = negotiationReplyPacket.getNegotiationReplyEntries();

        int num_bundles = negotiationReplyEntries.size();

        debug("Processing " + num_bundles + " bundles", INFO);

        for (int i = 0; i < num_bundles; i++) {


            NegotiationReplyEntry entry = negotiationReplyEntries.get(i);
            debug(entry.toString(), DEBUG);
            String fileName = entry.getBundleId();
            byte shouldSend = entry.getStatus();
            long offset = entry.getOffset();

            debug("File name:"+fileName, DEBUG);

            Bundle theBundle = mainActivity.getBundle(fileName);

            //If the negotiation reply ask not to send the bundle
            if(shouldSend==negotiationReplyPacket.DONTSEND){
                mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
            }
            else if(shouldSend==negotiationReplyPacket.SENDMETADATA){
                send_meta_data(theBundle);
                mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
            }
            else{
                if(offset > 0){
                    debug("[process_opportunistic_negotiation_reply]" +
                            " Will send "+entry.getBundleId()+" from "+offset, DEBUG);
                    filesToSend.add(entry);
                }
                else{
                    debug("[process_opportunistic_negotiation_reply]" +
                            " Will send "+entry.getBundleId()+" from the beginning", DEBUG);
//                    send_meta_data(theBundle);
                    filesToSend.add(entry);
                }
            }
        }

        sendOppFile(); //Start the process of sending bundles

    }


    /**
     * Will take in a bundle set-up timestamps
     * @param sentBundle
     */
    private void process_metadata(Bundle sentBundle) {
        debug("[process_metadata] Got metadata for ",DEBUG);
        debug(sentBundle.toString(), DEBUG);
        Bundle theBundle = mainActivity.getBundle(sentBundle.getBundleID());
        if(theBundle == null){
            theBundle = new Bundle(sentBundle.getBundleID(), device_Id);
            mainActivity.addBundle(theBundle);
        }
        else{
            debug("[process_metadata] Bundle will be sent starting from a specific offset", INFO);
            theBundle.addNode(mainActivity.DEVICE_ID);
            theBundle.addTransferTimeStamp(System.currentTimeMillis());
//            theBundle.addTransferTimeStamp(0);

            theBundle.addDisconnectTimeStamp(sentBundle.getDisconnect_time_start());
            theBundle.addDisconnectTimeStamp(sentBundle.getDisconnect_time_end());

            theBundle.addConnectTimeStamp(sentBundle.getConnect_time_start());
            theBundle.addConnectTimeStamp(sentBundle.getConnect_time_end());

            theBundle.addWaitingTimeStamp(sentBundle.getScan_time_start());
            theBundle.addWaitingTimeStamp(sentBundle.getScan_time_end());
            return;
        }

        theBundle.setSource(sentBundle.getSource());
        theBundle.setDestination(sentBundle.getDestination());
        theBundle.set_checkSum(sentBundle.getCheckSum());
        theBundle.setBundleSize(sentBundle.getBundleSize());
        theBundle.setNodes(sentBundle.getNodes());
        theBundle.setDisconnectTime(sentBundle.getDisconnectTime());
        theBundle.setTransferTime(sentBundle.getTransferTime());
        theBundle.setConnectionEstablishment(sentBundle.getConnectionEstablishment());
        theBundle.setWaitingDelay(sentBundle.getWaitingDelay());

        if(!sentBundle.isDelivered()) {
            theBundle.addNode(mainActivity.DEVICE_ID);
            theBundle.addTransferTimeStamp(System.currentTimeMillis());
//            theBundle.addTransferTimeStamp(0);

            theBundle.addDisconnectTimeStamp(sentBundle.getDisconnect_time_start());
            theBundle.addDisconnectTimeStamp(sentBundle.getDisconnect_time_end());

            theBundle.addConnectTimeStamp(sentBundle.getConnect_time_start());
            theBundle.addConnectTimeStamp(sentBundle.getConnect_time_end());

            theBundle.addWaitingTimeStamp(sentBundle.getScan_time_start());
            theBundle.addWaitingTimeStamp(sentBundle.getScan_time_end());
        }
        else{
            theBundle.delivered();
            mainActivity.addBundleToQueue(theBundle);
        }
    }

    /**
     * Send meta-data of the bundle
     * @param theBundle     The bundle that I will send meta-data of
     */
    private void send_meta_data(Bundle theBundle){
        try {
            theBundle.setDisconnect_time_start(mainActivity.getDisconnect_time_start());
            theBundle.setDisconnect_time_end(mainActivity.getDisconnect_time_end());

            theBundle.setConnect_time_start(mainActivity.getConnection_establishment_time_start());
            theBundle.setConnect_time_end(mainActivity.getConnection_establishment_time_end());

            theBundle.setScan_time_start(mainActivity.getScan_time_start());
            theBundle.setScan_time_end(mainActivity.getScan_time_end());
            debug("Sending meta-data for " + theBundle, DEBUG);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput output = new ObjectOutputStream(bos);

            output.writeObject(theBundle);

            Packet packet = new Packet(mainActivity.DEVICE_ID, device_Id, OPP_META_DATA,
                    0, bos.toByteArray());

            writeToNode(device_Id, packet);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This function will send all the files available in the stack of files to-be-sent.
     * This function Supports bundle transfer resume
     */
    public void sendOppFile(){
        if(!filesToSend.isEmpty()){
            NegotiationReplyEntry bundleEntry = filesToSend.peek();

            Bundle theBundle = mainActivity.getBundle(bundleEntry.getBundleId());
            send_meta_data(theBundle);

            String fileName = theBundle.getBundleID();

            try {

                RandomAccessFile f = new RandomAccessFile((MainActivity.rootDir + fileName), "r");
                f.seek(bundleEntry.getOffset());

                long FileSize = f.length();
                int chunkSize = 10240, len; //10 KB

                byte[] buf = new byte[chunkSize];
                debug("[OPPORTUNISTIC] Sending file "+fileName+" to "+device_Id, INFO);
//                mainActivity.debug("[OPPORTUNISTIC] Sending file "+fileName+" to "+device_Id);
                while((len = f.read(buf)) > 0){
                    DataPacket dataPacket = new DataPacket(fileName, FileSize, buf);

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutput output = new ObjectOutputStream(bos);
                    output.writeObject(dataPacket);
                    byte[] objectToArray = bos.toByteArray();

                    Packet packet = new Packet(mainActivity.DEVICE_ID, device_Id, DATA, len, objectToArray);

                    writeToNode(device_Id, packet);
                }

                debug("Done writing "+fileName, INFO);
//                mainActivity.debug("Done writing "+fileName);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e){
                debug("Failed to write to the Output File", ERROR);
                e.printStackTrace();
                return;
            }
        }
    }

    public synchronized void writeToNode(int nodeID, Packet outgoingPacket) {
        WorkerThread thread = mainActivity.connectionThreads[nodeID-1];
        if(thread != null)
            try {

                ObjectOutput output = new ObjectOutputStream(thread.outputStream);
                output.writeObject(outgoingPacket);

            } catch (IOException e) {
                e.printStackTrace();
                cancel();
            }
        else{
            debug(nodeID+" doesn't exist", ERROR);
        }
    }

    public void cancel(){
        debug("{cancel}",DEBUG);

        threadIsAlive = false;
        try {
            outputStream.close();
            debug("Removing "+device_Id+" from the pool of connections", INFO);


        } catch (IOException e) {
            e.printStackTrace();
        }


        mainActivity.removeNode(device_Id);
        debug("{cancel}",DEBUG);
    }
}
