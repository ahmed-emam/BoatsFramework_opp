package aemam.boatsframework_opp;

//import android.os.Bundle;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


import aemam.boatsframework_opp.model.Bundle;
import aemam.boatsframework_opp.model.Packet;

public class MainActivity extends AppCompatActivity {

    public static int DEVICE_ID = 0;
    public static String rootDir = Environment.getExternalStorageDirectory().toString() +
            "/MobiBots/boatsFrameWork_opportunistic/";
    private static Set<Integer> nearby_devices;
    private boolean[] nearby_devices_beats;
    public static final int  MAX_CONNECTIONS = 1024;
    WorkerThread[] connectionThreads;
    public Handler scheduler;
    public DatagramSocket broadcastSocket = null;
    public OutputStream commandCenterOutputStream;
    int iterations = 0;
    /*******************************************
     *          Logging Variables
     * *****************************************
     */
    public static final int TOAST_MSG = 0;
    public static final int TOAST_MSG_SHORT = 1;
    public static final int TEXT_MSG = 2;
    private static FileOutputStream logfile;
    public static final String TAG = "opportunistic";
    private static FileOutputStream locationFile;
    Ringtone alarmRingtone;
    /*******************************************
     *          Logging Variables
     * *****************************************
     */

    /*******************************************
     *          Bundle Variables
     * *****************************************
     */
    public ArrayList<Bundle> Bundles_repo;
    public Queue<Bundle> Bundles_queue;
    //TODO: Memory management: Change signature of HashMap from Bundle to BundleId (String)
    public HashMap<Bundle, Set<Integer>> Bundles_nodes_mapping;         //This is a mapping between
    //bundles and nodes that
    //have negotiated this bundle
    /*******************************************
     *          Bundle Variables
     * *****************************************
     */


    /*******************************************
     *          Routing Variables
     * *****************************************
     */
    private static HashMap<Integer, Integer> routingTable;
    /*******************************************
     *          Routing Variables
     * *****************************************
     */

    /*******************************************
     *   START List of time-stamped timers
     * *****************************************
     */
    public long connection_establishment_time_start = 0L;
    public long connection_establishment_time_end = 0L;
    public long disconnect_time_start = 0L;
    public long disconnect_time_end = 0L;
    public long disconnect_time = 0L;
    public long scan_time_start = 0L;
    public long scan_time_end = 0L;
    public long waiting_time = 0L;

    public long getDisconnect_time_start() {
        return disconnect_time_start;
    }
    public long getDisconnect_time_end() {
        return disconnect_time_end;
    }
    public long getScan_time_start() {
        return scan_time_start;
    }
    public long getScan_time_end() {
        return scan_time_end;
    }
    public long getConnection_establishment_time_start() {
        return connection_establishment_time_start;
    }
    public long getConnection_establishment_time_end() {
        return connection_establishment_time_end;
    }
    public long getWaiting_time() {
        return waiting_time;
    }
    /*******************************************
     *   END List of time-stamped timers
     * *****************************************
     */

    MainActivity theMainActivity;

    public String[] device_Wifi_adresses = {
            "D8:50:E6:83:D0:2A",
            "D8:50:E6:83:68:D0",
            "D8:50:E6:80:51:09",
            "24:DB:ED:03:47:C2",
            "24:DB:ED:03:49:5C",
            "8c:3a:e3:6c:a2:9f",
            "8c:3a:e3:5d:1c:ec",
            "c4:43:8f:f6:3f:cd",
            "f8:a9:d0:02:0d:2a"
    };


    public String[] device_ip_adresses = {
            "",
            "",
            "",
            "",
            "",
            "10.0.0.1",
            "10.0.0.2",
            "10.0.0.3",
            "10.0.0.4",
            "",
            ""
    };

    /**
     * TODO: Don't forget to change this to a static Handler to prevent memory leak
     */
    public Handler UIHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TOAST_MSG:
                    byte[] message = (byte[]) msg.obj;
                    String theMessage = new String(message, 0, msg.arg1);
                    Toast.makeText(getApplicationContext(), theMessage, Toast.LENGTH_LONG).show();
                    break;
                case TOAST_MSG_SHORT:
                    message = (byte[]) msg.obj;
                    theMessage = new String(message, 0, msg.arg1);
                    Toast.makeText(getApplicationContext() , theMessage,Toast.LENGTH_SHORT).show();
                    break;
                case TEXT_MSG:
                    TextView view = (TextView) findViewById(R.id.textView2);

                    message = (byte[]) msg.obj;
                    theMessage = new String(message, 0, msg.arg1);
                    theMessage += "\n";

                    view.append(theMessage);
                    break;
            }
        }
    };

    /**
     * According to the routing table,
     * who should I forward messages to for it to reach nodeID
     * @param nodeID        device's ID
     * @return
     */
    public synchronized int routePacket(int nodeID) {
        Integer sendToNode = routingTable.get(nodeID);
        if (sendToNode != null) {
            debug("Packet to " + nodeID + " send it to " + sendToNode);
            return sendToNode;
        } else
            return -1;
    }

//    /**
//     * Add a new route to the routing table
//     * @param destNode
//     * @param routeNode
//     */
//    public synchronized void addRoute(int destNode, int routeNode){
//        debug("Route to "+destNode+" through "+routeNode);
//        routingTable.put(destNode, routeNode);
//    }
//    public String printRoutingTable(){
////        Log.d(TAG, "Dest\t| Routes ");
//        String out = "Dest\t| Routes\n";
//
//        for(Integer entry : routeTable.keySet()){
//            out += entry.intValue()+"\t| ";
//            PriorityQueue<Route> routes = routeTable.get(entry);
//            for(Route route : routes){
//                out += route+" | ";
//            }
//            out += "\n";
//        }
//        Log.d(TAG, out);
//        return out;
//    }
//    public void removeRoute(int nodeID, int nextHop){
//        PriorityQueue<Route> routes = routeTable.get(nodeID);
//        if(routes != null){
//            for(Route route : routes){
//                if(route.getNextHop() == nextHop)
//                    routes.remove(route);
//            }
//
//            if(routes.size() == 0){
//                routeTable.remove(nodeID);
//            }
//        }
//    }
    /**
     * Print out debug messages if "D" (debug mode) is enabled
     * @param message
     */
    public void debug(String message) {

        Log.d(TAG, message);
        Message  toastMSG = theMainActivity.UIHandler.obtainMessage(TEXT_MSG);
        byte[] toastMSG_bytes =  (message).getBytes();
        toastMSG.obj = toastMSG_bytes;
        toastMSG.arg1 = toastMSG_bytes.length;
        theMainActivity.UIHandler.sendMessage(toastMSG);

    }


    public static synchronized String getTimeStamp() {
        return (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()));
    }

    /**
     * Function that write 'message' to the log file
     * @param message
     */
    public synchronized void log(String message) {
        StringBuilder log_message = new StringBuilder(26 + message.length());
        log_message.append(getTimeStamp());
        log_message.append(": ");
        log_message.append(message);
        log_message.append("\n");

        try {
            logfile.write(log_message.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    /**
     * Function that write 'message' to the log file
     * @param message
     */
    public synchronized void logLocation(String message) {
        StringBuilder log_message = new StringBuilder(26 + message.length());
        log_message.append(getTimeStamp());
        log_message.append(": ");
        log_message.append(message);
        log_message.append("\n");

        try {
            locationFile.write(log_message.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    private void initLogging() {
        File mediaStorageDir = new File(rootDir);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss").format(new Date());
        File file = new File(rootDir + "Device_" +DEVICE_ID + "_" +timeStamp + ".txt");
        Log.d(TAG, file.getPath());
        try {

            logfile = new FileOutputStream(file);

            file = new File(rootDir + "location.txt");
            if (!file.exists())
                file.createNewFile();
            locationFile = new FileOutputStream(file, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int findDevice_IpAddress(String ipAddress){
        for (int i = 0; i < device_ip_adresses.length; i++) {
            if (device_ip_adresses[i].equalsIgnoreCase(ipAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }
    /**
     * Return device's ID mapping according to the device's wifi mac address
     * @param deviceAddress     Bluetooth Address
     * @return                  device ID
     */
    public int findDevice_Wifi(String deviceAddress) {
        for (int i = 0; i < device_Wifi_adresses.length; i++) {
            if (device_Wifi_adresses[i].equalsIgnoreCase(deviceAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }
    /**
     * Return the bundle with file name "fileName"
     * @param bundleID
     * @return
     */
    public Bundle getBundle(String bundleID){
        for(int i = 0; i < Bundles_repo.size(); i++){
            Bundle theOppBundle = Bundles_repo.get(i);
            String file_name = theOppBundle.getBundleID();

            if(file_name.equals(bundleID)){
                return theOppBundle;
            }
        }
        return null;
    }
    public boolean removeBundle(String bundleID){
        File f = new File(rootDir+bundleID);
        if(f.exists())
            f.delete();

        return Bundles_repo.remove(bundleID);
    }
    public synchronized void addBundle(Bundle theBundle){
        Bundles_repo.add(theBundle);
    }

    public synchronized void addBundleToQueue(Bundle theBundle){
        for(Bundle bundle: Bundles_queue){
            if(bundle.isEqual(theBundle)){
                return;
            }
        }
        Bundles_queue.add(theBundle);
    }
    /**
     * Synchronized function to add the thread responsible for communication with nodeID "node"
     * @param thread    Communication thread
     * @param node      nodeID
     */
    public synchronized void addNode(WorkerThread thread,int node){
        debug("Connected to "+node);
        log("Connected to "+node);
        try{
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();

        }
        catch (Exception e){
            e.printStackTrace();
        }
//        addRoute(node, new Route(node, 1));
        connectionThreads[node-1] = thread;
    }

    Runnable stopAlarm = new Runnable() {
        @Override
        public void run() {
            if(alarmRingtone.isPlaying())
                alarmRingtone.stop();
        }
    };

    /**
     * This function will add the nodeId to the set of nodes that I have negotiated bundle-exchange,
     * It will do this to all the bundles in my repo
     * @param nodeID        nodeID
     */
    public synchronized void addNodeToBundleNegotiationMapping(int nodeID){
        for(Bundle theBundle : Bundles_repo){
            Bundles_nodes_mapping.get(theBundle).add(nodeID);
        }
    }
    /**
     * This function will add the nodeId to the set of nodes that I have negotiated bundle-exchange,
     * It will do this to all the bundles in my repo
     * @param nodeID        nodeID
     */
    public synchronized void addNodeToBundleNegotiationMapping(Bundle bundle,int nodeID){
        if(bundle != null) {
            Set<Integer> listOfNodes;
            if((listOfNodes =  Bundles_nodes_mapping.get(bundle)) != null) {
                listOfNodes.add(nodeID);
            }
        }
        else
            debug("ERROR: bundle is NULL");

    }
    /**
     * This function will add the nodeId to the set of nodes that I have negotiated bundle-exchange,
     * It will do this to all the bundles in my repo
     * @param nodeID        nodeID
     */
    public synchronized void removeNodeToBundleNegotiationMapping(Bundle bundle,int nodeID){
        if(bundle != null) {
            Set<Integer> listOfNodes;
            if((listOfNodes =  Bundles_nodes_mapping.get(bundle)) != null) {
                listOfNodes.remove(nodeID);
            }
        }
        else
            debug("ERROR: bundle is NULL");

    }

    /**
     * Synchronized function to remove the thread responsible for communication with nodeID "node"
     * @param node  nodeID
     */
    public synchronized void removeNode(int node){
        debug("Disconnecting from "+node);
        log("Disconnecting from "+node);
//        try{
//            if(alarmRingtone.isPlaying())
//                alarmRingtone.stop();
//            alarmRingtone.play();
//            scheduler.postDelayed(stopAlarm, 4000);
//        }
//        catch (Exception e){
//            e.printStackTrace();
//        }
//        removeRoutesAssociatedWithNode(node);

        connectionThreads[node-1] = null;

//        nearby_devices.remove(node);
//        nearby_devices_beats[node-1] = false;
    }
    /**
     * This function will tell me if I should connect to the node with ID (nodeID) or not.
     * It goes through the bundles and checks if we there are any bundles that should be
     * sent to the device with "nodeID"
     * @param nodeID
     * @return
     */
    public boolean shouldConnect(int nodeID) {
        if(connectionThreads[nodeID-1] != null)
            return false;

        for (Bundle theBundle : Bundles_queue) {
            Set<Integer> nodesNegotiated = Bundles_nodes_mapping.get(theBundle);

            if(nodesNegotiated != null && nodesNegotiated.contains(nodeID)){
                continue;
            }
            else{
                debug("Node mapping doesnt have "+nodeID);
            }
            if (theBundle.isDelivered()) {
                return true;
            }
            else {

                if (DEVICE_ID == theBundle.getDestination())
                    continue;

                if (nodeID == theBundle.getSource()) {
                    continue;
                }
                boolean dont_choose = false;
                ArrayList<Integer> nodes = theBundle.getNodes();
                for (int m = 0; m < nodes.size(); m++) {
                    if (nodes.get(m) == nodeID) {
                        dont_choose = true;
                    }
                }
                if (dont_choose)
                    continue;


                return true;
            }
        }
        return false;
    }
    @Override
    protected void onStart() {
        super.onStart();
        // Hook up to the GPS system
        LocationManager gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria c = new Criteria();
        c.setAccuracy(Criteria.ACCURACY_FINE);
        c.setPowerRequirement(Criteria.NO_REQUIREMENT);
        String provider = gps.getBestProvider(c, false);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        gps.requestLocationUpdates(provider, 0, 0, locationListener);
    }

    /**
     * Handles GPS updates by calling the appropriate update.
     */
    private LocationListener locationListener = new LocationListener() {
        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {

            String a = String.format("onStatusChanged: provider = %s, status= %d", provider, status);
            Log.w(TAG, a);
        }

        public void onProviderEnabled(String provider) {
            Log.w(TAG, "onProviderEnabled");
        }

        public void onProviderDisabled(String provider) {
        }

        public void onLocationChanged(Location location) {
// Convert from lat/long to UTM coordinates
            debug("Current Location: " + location);
            String out = "";
            out += location.getLatitude() + "\t" + location.getLongitude() + "\t" + location.getAltitude();

            if(location.hasSpeed())
                out += "\t" + location.getSpeed();
            else
                out += "\t0.0";

            if(location.hasBearing())
                out += "\t" + location.getBearing();
            else
                out += "\t0.0";

            logLocation(out);
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from GPS updates
        LocationManager gps;
        gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        gps.removeUpdates(locationListener);

    }

    /**
     * Function to log all the bundles available at the current node
     */
    public void log_bundles(){
        log("===============Logging Bundles===============");
        debug("===============Logging Bundles===============");
        for(int i = 0; i < Bundles_repo.size(); i++) {

            Bundle theBundle = Bundles_repo.get(i);
            log("File Name:"+theBundle.getBundleID()+
                    "\tDelivered: "+ (theBundle.isDelivered()? "Yes":"No")+
                    "\tDestination:"+theBundle.getDestination()
                    +"\tSource:"+theBundle.getSource());

            debug("File Name:"+theBundle.getBundleID()+
                    "\tDelivered: "+ (theBundle.isDelivered()? "Yes":"No")+
                    "\tDestination:"+theBundle.getDestination()
                    +"\tSource:"+theBundle.getSource());

            ArrayList<Integer> nodes = theBundle.getNodes();
            String nodes_in_String = "Nodes:\t\t\t";
            for(int m = 0; m < nodes.size(); m++){
                nodes_in_String += (nodes.get(m)+"\t"+nodes.get(m)+"\t");
            }
            log(nodes_in_String);
            debug(nodes_in_String);


            ArrayList<Long> disconnectTime = theBundle.getDisconnectTime();
            String disconnectTimes_in_String = "Disconnect_Time:\t";
            for(int m = 0; m < disconnectTime.size(); m++){
                disconnectTimes_in_String += (disconnectTime.get(m)+"\t");
            }
            log(disconnectTimes_in_String);
            debug(disconnectTimes_in_String);


            ArrayList<Long> meeting_delay = theBundle.getWaitingDelay();
            String meetingDelay_in_String = "Scan_Time:\t\t";
            for(int m = 0; m < meeting_delay.size(); m++){
                meetingDelay_in_String += (meeting_delay.get(m) + "\t");
            }
            log(meetingDelay_in_String);
            debug(meetingDelay_in_String);


            ArrayList<Long> connect_delay = theBundle.getConnectionEstablishment();
            String connectDelay_in_String = "Connect_Time:\t\t";
            for(int m = 0; m < connect_delay.size(); m++){
                connectDelay_in_String += (connect_delay.get(m)+"\t");
            }
            log(connectDelay_in_String);
            debug(connectDelay_in_String);

            ArrayList<Long> transferTimes = theBundle.getTransferTime();
            String transferTimes_in_String = "Transfer_Time:\t\t";
            for(int m = 0; m < transferTimes.size(); m++){
                transferTimes_in_String += (transferTimes.get(m)+"\t");
            }
            log(transferTimes_in_String);
            debug(transferTimes_in_String);



            log("CheckSum: "+theBundle.getCheckSum());

        }
        log("===============Done Logging Bundles===============");
        debug("===============Done Logging Bundles===============");
    }

    public void check_nearby_devices(){
        scheduler.removeCallbacks(scanWifiNearbyDevices);
        for(Integer deviceID : nearby_devices){
            int deviceid = deviceID.intValue();
            debug("Should I connect to "+deviceid);
            if(shouldConnect(deviceID)){
                debug("Will connect to "+deviceid);

                //TODO: Connect to device
                scan_time_end = System.currentTimeMillis();
                connection_establishment_time_start = System.currentTimeMillis();

                WifiAdhocClientThread wifiClientThread = new WifiAdhocClientThread(device_ip_adresses[deviceid-1]);
                wifiClientThread.start();


            }
        }
        scheduler.postDelayed(checkBundles, 1000);

    }

    //A runnable that does wifi network scan every X seconds
    private Runnable scanWifiNearbyDevices = new Runnable() {
        @Override
        public void run() {
            debug("Scanning");
            check_nearby_devices();
        }
    };

    private Runnable checkBundles = new Runnable() {
        @Override
        public void run() {
            if(Bundles_queue.isEmpty())
            {
                scheduler.postDelayed(checkBundles, 10000);
            }
            else{
                debug("waiting counter started");
                scan_time_start = System.currentTimeMillis();
                debug("We have something to say");
                scheduler.removeCallbacks(checkBundles);
                scheduler.postDelayed(scanWifiNearbyDevices, 0);
            }
        }
    };
    /**
     *
     */
    public void switch_networks(){

        disconnect_time_end = System.currentTimeMillis();
        scheduler.postDelayed(checkBundles, 0);
    }
    public void start_experiment(View v){
        sendToCommandCenter("Started the Experiment");
        log("Experiment Started");
        String filename ;
//
//
        Bundle newBundle;
//        filename   = file_name;
//        newBundle = new Opp_Bundle(9,7, "5MB_20141111_140119.txt");
//
//        newBundle.set_checkSum(hashFile("5MB_20141111_140119.txt", "MD5"));
//
//        oppBundles_repo.add(newBundle);
//        oppBundles_queue.add(newBundle);


        newBundle = new Bundle(9,7, "10M_20141111_140119.txt", DEVICE_ID);
        File f = new File(rootDir+"10M_20141111_140119.txt");
        if(f.exists())
            newBundle.setBundleSize(f.length());
//        newBundle.set_checkSum(hashFile("10M_20141111_140119.txt", "MD5"));

        Bundles_repo.add(newBundle);
        Bundles_queue.add(newBundle);
        Bundles_nodes_mapping.put(newBundle, new HashSet<Integer>());

//        newBundle = new Opp_Bundle(9,7, filename);
//        newBundle.set_checkSum(hashFile(filename, "MD5"));
//        oppBundles_repo.add(newBundle);
//        oppBundles_queue.add(newBundle);


        switch_networks();

        disconnect_time_end = 0;
        disconnect_time_start = 0;
    }

    public void start_experiment(){
        sendToCommandCenter("Started the Experiment");
        log("Experiment Started");
        String filename ;
//
//
        Bundle newBundle;
//        filename   = file_name;
//        newBundle = new Opp_Bundle(9,7, "5MB_20141111_140119.txt");
//
//        newBundle.set_checkSum(hashFile("5MB_20141111_140119.txt", "MD5"));
//
//        oppBundles_repo.add(newBundle);
//        oppBundles_queue.add(newBundle);

        newBundle = new Bundle(9,7, "10M_20141111_140119.txt", DEVICE_ID);
        File file = new File(rootDir + "10M_20141111_140119.txt");
        newBundle.setBundleSize(file.length());
//        newBundle.set_checkSum(hashFile("10M_20141111_140119.txt", "MD5"));

        Bundles_repo.add(newBundle);
        Bundles_queue.add(newBundle);

//        newBundle = new Opp_Bundle(9,7, filename);
//        newBundle.set_checkSum(hashFile(filename, "MD5"));
//        oppBundles_repo.add(newBundle);
//        oppBundles_queue.add(newBundle);


        switch_networks();

        disconnect_time_end = 0;
        disconnect_time_start = 0;
    }
    /**
     * This is a hack to check if IBSS mode is enabled or not
     * @return True     if IBSS is on
     *         False    Otherwise
     */
    public boolean checkIBSSMode(){
        Process su = null;
        DataOutputStream stdin = null ;
        try {
            su = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
            stdin = new DataOutputStream(su.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            stdin.writeBytes("iw dev wlan0 info | grep type | awk {'print $2'}\n");
            InputStream stdout = su.getInputStream();
            byte[] buffer = new byte[1024];

            //read method will wait forever if there is nothing in the stream
            //so we need to read it in another way than while((read=stdout.read(buffer))>0)
            int read = stdout.read(buffer);
            String out = new String(buffer, 0, read);

            if(out.contains("IBSS")){

                return true;
            }else{
                return false;
            }

        }catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public void newExperiment(){

        debug("===========Starting a NEW EXPERIMENT===========");
        log("===================NEW EXPERIMENT===================");

//        clear_networks();

        WifiAdhocServerThread serverThread = new WifiAdhocServerThread(theMainActivity);
        serverThread.start();

        WifiBroadcast_server broadcast_server = new WifiBroadcast_server();
        broadcast_server.start();

        WifiBroadcast_client broadcast_client = new WifiBroadcast_client();
        broadcast_client.start();

        File file;
        Bundle theBundle;

//        file  = new File(rootDir + "20M_20141111_140119.txt");
//        theBundle = getBundle("20M_20141111_140119.txt");
//        if(theBundle != null){
//            Bundles_repo.remove(theBundle);
//        }
//        if(DEVICE_ID != 9) {
//
//            if (file.exists()) {
//                file.delete();
//            }
//        }


        file = new File(rootDir + "10M_20141111_140119.txt");
        theBundle = getBundle("10M_20141111_140119.txt");
        if(theBundle != null){
            Bundles_repo.remove(theBundle);
        }
        if(DEVICE_ID != 9) {

            if (file.exists()) {
                file.delete();
            }
        }

//
    }
    public void disconnect(View v){
        for(int i = 0; i < connectionThreads.length; i++){
            if(connectionThreads[i] != null){
                connectionThreads[i].cancel();
                connectionThreads[i] = null;
            }
        }
    }
    public void newExperiment(View v){

        debug("===========Starting a NEW EXPERIMENT===========");
        log("===================NEW EXPERIMENT===================");

//        clear_networks();

        WifiAdhocServerThread serverThread = new WifiAdhocServerThread(theMainActivity);
        serverThread.start();

        WifiBroadcast_server broadcast_server = new WifiBroadcast_server();
        broadcast_server.start();

        WifiBroadcast_client broadcast_client = new WifiBroadcast_client();
        broadcast_client.start();

        File file;
        Bundle theBundle;

//        file  = new File(rootDir + "20M_20141111_140119.txt");
//        theBundle = getBundle("20M_20141111_140119.txt");
//        if(theBundle != null){
//            Bundles_repo.remove(theBundle);
//        }
//        if(DEVICE_ID != 9) {
//
//            if (file.exists()) {
//                file.delete();
//            }
//        }


        file = new File(rootDir + "10M_20141111_140119.txt");
        theBundle = getBundle("10M_20141111_140119.txt");
        if(theBundle != null){
            Bundles_repo.remove(theBundle);
        }
        if(DEVICE_ID != 9) {

            if (file.exists()) {
                file.delete();
            }
        }

    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        theMainActivity = this;


        WifiManager wifiMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        DEVICE_ID = findDevice_Wifi(wifiInfo.getMacAddress());
        initLogging();

        connectionThreads = new WorkerThread[MAX_CONNECTIONS];
        routingTable = new HashMap<>();
        nearby_devices = new HashSet<>();
        nearby_devices_beats = new boolean[MAX_CONNECTIONS];

        Bundles_repo = new ArrayList<>();
        Bundles_queue = new LinkedList<>();
        Bundles_nodes_mapping = new HashMap<>();
        scheduler = new Handler();


        if(checkIBSSMode()){
            debug("You are in IBSS mode");
        }
        else{
            try{
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            }
            catch (Exception e){
                e.printStackTrace();
            }

            debug("NOT in IBSS mode");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finish();
        }

        new CommandsThread().start();
    }

    public void startCommunicationThreads(int nodeID,
                                          InputStream inputStream, OutputStream outputStream,
                                          MainActivity mainActivity){


        BlockingQueue<Packet> incomingPackets = new LinkedBlockingQueue<>();

        ReadingThread readingThread = new ReadingThread(inputStream, incomingPackets,nodeID);


        WorkerThread workerThread = new WorkerThread(outputStream,incomingPackets,  nodeID,
                mainActivity);
        addNode(workerThread, nodeID);
        readingThread.start();
        workerThread.start();


    }

    public void sendToCommandCenter(String msg){
        if(commandCenterOutputStream != null)
            try {
                commandCenterOutputStream.write((msg + "\n\r").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
    }


    public class CommandsThread extends Thread{

        ServerSocket serverSocket = null;
        boolean serverOn = true;
        Socket client = null;

        public CommandsThread() {

            try {
                serverSocket = new ServerSocket(8888);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void writeInstructions(OutputStream outputStream){
            String out = "\t\t*****WELCOME to the command center for Scenario 3 Experiment*****\n" +
                    "Usage: <command>\n" +
                    "command\tDescription\n" +
                    "start\tStart Experiment\n" +
                    "new\tNew Experiment\n" +
//                    "remove <DestID> <NextHop>\tRemove route (DestID, NextHop) from routing table\n" +
                    "\n\r";
            try {
                outputStream.write(out.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
//            scheduler.postDelayed(printRoutingTable, 2000);
            while(serverOn){
                try {
                    client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());


                    commandCenterOutputStream= client.getOutputStream();
                    writeInstructions(commandCenterOutputStream);

                    InputStream inputStream = client.getInputStream();
                    int len;
                    byte[] buf = new byte[1024];
                    commandCenterOutputStream.write("Type in your command: ".getBytes());
                    while((len = inputStream.read(buf)) > 0){
                        String newCommand = new String(buf, 0, len);
                        Log.d(TAG, "COMMAND: "+newCommand);
                        sendToCommandCenter("Your command was: "+newCommand);
                        Scanner lineScanner = new Scanner(newCommand);

                        if(newCommand.contains("start")){
                            lineScanner.nextLine();
                            start_experiment();
                        }
                        else if(newCommand.contains("new")){
                            lineScanner.nextLine();
                            newExperiment();
                        }
                        else{
                            debug("Invalid command");
                            commandCenterOutputStream.write((lineScanner.nextLine() +
                                    "\tNOT SUPPORTED\n\r").getBytes());
                        }

                        commandCenterOutputStream.write("Type in your command: ".getBytes());
                    }
                }catch(IOException e){
                    cancel();
                    e.printStackTrace();
                }
            }
        }


        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();
                if(client!=null)
                    client.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class WifiAdhocServerThread extends Thread {
        ServerSocket serverSocket = null;
        boolean serverOn = true;

        public WifiAdhocServerThread(MainActivity activity) {

            try {
                serverSocket = new ServerSocket(8000);

                Log.i("TelnetServer", "ServerSocket Address: " + serverSocket.getLocalSocketAddress());

            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (serverOn) {
                try {
                    Log.d(TAG, "Server Thread");
                    Log.i(TAG, "Started: " + serverSocket.getLocalSocketAddress());
                    Socket client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());
                    int deviceId = findDevice_IpAddress(client.getInetAddress().getHostAddress());
                    if(connectionThreads[deviceId-1] != null) {
                        client.close();
                    }
                    else {
                        debug("SERVER Connected to " + deviceId + " " + client.getInetAddress().getHostAddress());
                        startCommunicationThreads(deviceId,
                                client.getInputStream(), client.getOutputStream(), theMainActivity);
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void printConnectedNodes(){
        for(int i = 0; i < connectionThreads.length; i++){
            if(connectionThreads[i] != null){
                debug("Connected to "+(i+1));
                sendToCommandCenter("Connected to "+(i+1));
            }
        }

        for(Bundle theBundle : Bundles_nodes_mapping.keySet()){
            Log.i(TAG, theBundle.getBundleID());
            for(Integer nodeId : Bundles_nodes_mapping.get(theBundle))
                Log.i(TAG, nodeId.toString());
        }
    }


    public class WifiAdhocClientThread extends Thread {
        String hostAddress;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        int device_Id;


        public WifiAdhocClientThread(
                String host) {
//            mainActivity = activity;
            hostAddress = host;
        }

        @Override
        public void run() {
            /**
             * Listing 16-26: Creating a client Socket
             */
            int timeout = 500;
            int port = 8000;

            Socket socket = new Socket();
            try {
                device_Id = findDevice_IpAddress(hostAddress);
                debug("Connecting to " + device_Id + " @ " + hostAddress);
                socket.bind(null);
                socket.connect((new InetSocketAddress(hostAddress, port)), 500);

                debug("[CLIENT] Connected to "+device_Id+" "+hostAddress);

                if(connectionThreads[device_Id-1] != null) {
                    socket.close();
                }
                else {
                    startCommunicationThreads(device_Id,
                            socket.getInputStream(), socket.getOutputStream(), theMainActivity);
                }
//                otherConnection = new ConnectionThread(mainActivity, socket.getInputStream(),
//                        socket.getOutputStream(), true, device_Id);
//                otherConnection.start();
//                addNode(otherConnection, device_Id);

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
//                scheduler.postDelayed(scanWifiNearbyDevices, 500);
            }

        }
    }


    private class WifiBroadcast_server extends Thread{
        int port = 5555;
        public WifiBroadcast_server(){
            if(broadcastSocket == null)
                try {
                    broadcastSocket = new DatagramSocket(port);
                    broadcastSocket.setBroadcast(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            debug("Started advertising presence");
            scheduler.postDelayed(updateNearbyDevices, 1000);
            while(true){
                try {
                    String messageStr = "Device " + DEVICE_ID;
                    byte[] messageByte = messageStr.getBytes();

                    InetAddress group = InetAddress.getByName("10.0.0.255");
                    DatagramPacket packet = new DatagramPacket(messageByte, messageByte.length, group, port);
//                    debug("Broadcasting "+messageStr);
                    broadcastSocket.send(packet);
                    printConnectedNodes();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                catch (IOException e){
                    e.printStackTrace();
                    break;

                }
            }
        }
    }

    Runnable updateNearbyDevices = new Runnable() {
        @Override
        public void run() {
            iterations++;
            //if 3 seconds have passed check for nearby devices that I haven't heard from
            //and remove them from the list of nearby devices
            if(iterations % 3 == 0){
                for(int i = 0; i < MAX_CONNECTIONS; i++){
                    if(!nearby_devices_beats[i])
                        nearby_devices.remove(i+1);
                }
            }
            nearby_devices_beats = new boolean[MAX_CONNECTIONS];
            scheduler.postDelayed(this, 1000);
        }
    };

    private class WifiBroadcast_client extends Thread{
        int port = 5555;
        public WifiBroadcast_client(){

            if(broadcastSocket == null)
                try {
                    broadcastSocket = new DatagramSocket(port);
                    broadcastSocket.setBroadcast(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            while(true){
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    broadcastSocket.receive(packet);
                    byte[]data = packet.getData();

                    String messageStr = new String(data, 0,packet.getLength());

                    Scanner stringScanner = new Scanner(messageStr);
                    stringScanner.next();
                    int deviceID = stringScanner.nextInt();
                    if(deviceID != DEVICE_ID) {
                        nearby_devices_beats[deviceID-1] = true;
//                        debug("Found device "+deviceID);
                        sendToCommandCenter("Found device "+deviceID);
                        nearby_devices.add(deviceID);
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }
}
