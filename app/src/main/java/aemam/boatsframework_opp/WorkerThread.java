package aemam.boatsframework_opp;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import aemam.boatsframework_opp.model.Bundle;
import aemam.boatsframework_opp.model.DataPacket;
import aemam.boatsframework_opp.model.NegotiationPacket;
import aemam.boatsframework_opp.model.NegotiationReplyPacket;
import aemam.boatsframework_opp.model.Packet;

/**
 * Created by aemam on 12/27/15.
 *
 * Consumes messages from the queue "buffer" and implement logic for device to device data transfer
 *
 **/

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


    private static final String TAG = "Connection_Manager";
    InputStream inputStream;
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
    Queue<Bundle> filesToSend;
    Queue<Bundle> filesToReceive;

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

                if (MainActivity.DEVICE_ID == packet.getDestId()) {
                    byte packetType = packet.getType();
                    //Switch on Packet type
                    switch (packetType) {
                        case DATA:
                            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            DataPacket dataPacket = (DataPacket) objectInputStream.readObject();
                            debug(dataPacket.toString(), INFO);

                            String fileName = dataPacket.getFilename();
                            File f = new File((MainActivity.rootDir + dataPacket.getFilename()));
                            if(!f.exists()){
                                log("Receiving file:" + fileName);

                                Bundle theBundle = mainActivity.getBundle(fileName);
                                debug(theBundle.toString(), DEBUG);
                                theBundle.set_start_recv_time(System.currentTimeMillis());
                            }

                            FileOutputStream fileOutputStream = new FileOutputStream(f, true);


                            fileOutputStream.write(dataPacket.getData(), 0, packet.getLength());
                            fileOutputStream.close();


                            //I received the whole file
                            if(f.length() == dataPacket.getFileLength()) {
                                t_receive_end = System.currentTimeMillis();
                                debug("Received "+fileName, INFO);
                                mainActivity.debug("Received "+fileName);
                                log("File " + fileName + " is received");

                                //If this is an opportunistic data packet


                                Bundle theBundle = mainActivity.getBundle(fileName);
                                theBundle.set_end_recv_time(System.currentTimeMillis());

                                //Edit data transfer duration
                                long transferTime = theBundle.getEnd_recv_time() -
                                        theBundle.getStart_recv_time();

                                debug("Transfer took " + transferTime, INFO);
                                theBundle.setTransferTimeStamp(theBundle.getStart_recv_time(),
                                        theBundle.getEnd_recv_time());

                                //Remove this bundle from the bundles to be received queue
                                filesToReceive.remove(theBundle);

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
                            mainActivity.addNodeToBundleNegotiationMapping(mainActivity.getBundle(fileName), device_Id);

                            Bundle newBundle = mainActivity.getBundle(fileName);
                            if(newBundle.getDestination() == device_Id){
                                newBundle.delivered();
                            }
                            filesToSend.remove(newBundle);
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
                            debug("Terminating Connection with "+device_Id, INFO);
                            cancel();

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

                    terminateConn();
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


        for(Bundle theBundle : filesToReceive){
            debug("[RECEIVING]"+theBundle.getFileName()+" will be removed", INFO);
            File f = new File((MainActivity.rootDir + theBundle.getFileName()));
            if(f.exists())
                f.delete();

            mainActivity.Bundles_repo.remove(theBundle);
            mainActivity.Bundles_nodes_mapping.remove(theBundle);
            mainActivity.Bundles_queue.remove(theBundle);
        }
        for(Bundle theBundle : filesToSend){
            debug("[SENDING]" + theBundle.getFileName() + " will be removed", INFO);

            mainActivity.removeNodeToBundleNegotiationMapping(theBundle, device_Id);
        }

        mainActivity.switch_networks();
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
            negotiationPacket.addBundle(thebundle.getFileName(), thebundle.isDelivered());

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
    private void process_opportunistic_negotiation(NegotiationPacket packet) {

        ArrayList<String> bundleIds = packet.getBundleIds();
        ArrayList<Boolean> delivered = packet.getDelivered();
        NegotiationReplyPacket negotiationReplyPacket = new NegotiationReplyPacket();
        for(int i = 0; i < bundleIds.size(); i++){
            //Get the bundle from my repository
            boolean isDelivered = delivered.get(i);
            String bundleId = bundleIds.get(i);
            Bundle theBundle = mainActivity.getBundle(bundleId);
//            NegotiationReplyPacket.NegotiationReplyEntry negotiationReplyEntry = new NegotiationReplyPacket()
            //If the bundle sent in the negotiation has already reached its destination
            //Check if I have the bundle in my repo:
            //      If it exists then mark it as delivered
            //      If it doesn't exist then add its ID and mark it as delivered, this would
            //          allow me to share the bundle's status with other nodes
            if(isDelivered){
                //if it doesn't exist in my bundle repo
                if(theBundle == null){
                    theBundle = new Bundle(bundleId, mainActivity.DEVICE_ID);
                    theBundle.delivered();
                    mainActivity.Bundles_repo.add(theBundle);
                    negotiationReplyPacket.addBundle(bundleId, (byte)2);

                }
                else{
                    theBundle.delivered();
                }
                //Tell all other nodes about the status of the bundle
                //TODO: Think about floods of updates
                if(!mainActivity.Bundles_queue.contains(theBundle)){
                    mainActivity.Bundles_queue.add(theBundle);
                }

                mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
            }
            else{
                if(theBundle == null){
                    theBundle = new Bundle(bundleId, mainActivity.DEVICE_ID);
                    debug("Will receive\n" + theBundle.toString(), INFO);

                    negotiationReplyPacket.addBundle(bundleId, (byte)1);

                    //Add the bundle to the list of bundles to be received
                    filesToReceive.add(theBundle);
                    //Add the bundle to the repository of bundles
                    mainActivity.Bundles_repo.add(theBundle);
                    //TODO: Check sanity of the coming statement, if i add it to the queue before
                    //  receiving the bundle what will happen
                    mainActivity.Bundles_queue.add(theBundle);
                    mainActivity.Bundles_nodes_mapping.put(theBundle, new HashSet<Integer>());

                }
                else{
                    negotiationReplyPacket.addBundle(bundleId, (byte)0);
                    mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
                }
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

        writeToNode(device_Id, packet);
    }

    private void process_opportunistic_negotiation_reply(NegotiationReplyPacket negotiationReplyPacket){
        ArrayList<String> bundleIds = negotiationReplyPacket.getBundleIds();
        ArrayList<Byte> shouldSendArray = negotiationReplyPacket.getSendIt();

        int num_bundles = bundleIds.size();
//        mainActivity.addNodeToBundleNegotiationMapping(device_Id);
        debug("Processing " + num_bundles + " bundles", INFO);

        for (int i = 0; i < num_bundles; i++) {
//                    int destination = inputStream.readInt();    //Bundle Destination node ID

//                int fileNameLength = inputStream.readInt();//File name length
//                byte[] fileName_bytes = new byte[fileNameLength];
//                inputStream.readFully(fileName_bytes);     //file name
            String fileName = bundleIds.get(i);
            byte shouldSend = shouldSendArray.get(i);

            debug("File name:"+fileName, DEBUG);

            Bundle theBundle = mainActivity.getBundle(fileName);

            //Send meta-data and file data
            if(shouldSend==1) {
                //Sanity check, I should own that bundle at all times
                if (theBundle != null) {
                    debug("Will Send " + fileName + " bundle", DEBUG);
                    filesToSend.add(theBundle);
                } else
                    debug("ERROR: there was a problem with negotiation", ERROR);
            }
            //Send meta-data only
            else if(shouldSend == 2){
                if(theBundle != null)
                    send_meta_data(theBundle);
                mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
            }
            //Do nothing, the peer has meta-data of the bundle
            else if(shouldSend == 0){
                mainActivity.addNodeToBundleNegotiationMapping(theBundle, device_Id);
            }
        }

        sendOppFile(); //Start the process of sending bundles

    }

    private void process_metadata(Bundle sentBundle){
        Bundle theBundle = mainActivity.getBundle(sentBundle.getFileName());
        if(theBundle != null){
            theBundle.setSource(sentBundle.getSource());
            theBundle.setDestination(sentBundle.getDestination());
            theBundle.set_checkSum(sentBundle.getCheckSum());

            theBundle.setNodes(sentBundle.getNodes());
            theBundle.setDisconnectTime(sentBundle.getDisconnectTime());
            theBundle.setTransferTime(sentBundle.getTransferTime());
            theBundle.setConnectionEstablishment(sentBundle.getConnectionEstablishment());
            theBundle.setWaitingDelay(sentBundle.getWaitingDelay());

            if(!theBundle.isDelivered()) {
                theBundle.addNode(mainActivity.DEVICE_ID);
                theBundle.addTransferTimeStamp(0);
                theBundle.addTransferTimeStamp(0);

                theBundle.addDisconnectTimeStamp(sentBundle.getDisconnect_time_start());
                theBundle.addDisconnectTimeStamp(sentBundle.getDisconnect_time_end());

                theBundle.addConnectTimeStamp(sentBundle.getConnect_time_start());
                theBundle.addConnectTimeStamp(sentBundle.getConnect_time_end());

                theBundle.addWaitingTimeStamp(sentBundle.getScan_time_start());
                theBundle.addWaitingTimeStamp(sentBundle.getScan_time_end());
            }
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
     * This function will send all the files available in the stack of files
     * to be sent
     */
    public void sendOppFile(){
        if(!filesToSend.isEmpty()){
            Bundle theBundle = filesToSend.peek();
            send_meta_data(theBundle);


            String fileName = theBundle.getFileName();

            try {
                FileInputStream  inputStream = new FileInputStream((MainActivity.rootDir + fileName));

                int FileSize = inputStream.available();
                int chunkSize = 10240, len; //10 KB

                byte[] buf = new byte[chunkSize];
                debug("[OPPORTUNISTIC] Sending file "+fileName+" to "+device_Id, DEBUG);
                mainActivity.debug("[OPPORTUNISTIC] Sending file "+fileName+" to "+device_Id);
                while((len = inputStream.read(buf)) > 0){
                    DataPacket dataPacket = new DataPacket(fileName, FileSize, buf);

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutput output = new ObjectOutputStream(bos);
                    output.writeObject(dataPacket);
                    byte[] objectToArray = bos.toByteArray();

                    Packet packet = new Packet(MainActivity.DEVICE_ID, device_Id, DATA, len, objectToArray);

                    writeToNode(device_Id, packet);
                }

                debug("Done writing "+fileName, DEBUG);
                mainActivity.debug("Done writing "+fileName);
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
        threadIsAlive = false;
        try {
            outputStream.close();
            debug("Removing "+device_Id+" from the pool of connections", INFO);


        } catch (IOException e) {
            e.printStackTrace();
        }


        mainActivity.removeNode(device_Id);
    }
}
