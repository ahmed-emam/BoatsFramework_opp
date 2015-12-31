package aemam.boatsframework_opp;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;

import aemam.boatsframework_opp.model.Packet;

/**
 * Created by aemam on 12/27/15.
 */
public class ReadingThread extends Thread {

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
    private static final byte HEARTBEAT = 1;
    private static final byte STREAM = 2;
    private static final byte DATA = 3;

    private static final byte DELETE = 5;
    private static final byte TERMINATE = 10;    //Terminate connection
    /**************************************
     *             Packet Types
     * *************************************
     */



    InputStream inputStream;
    private static final String TAG = "Connection_Manager";

    int device_id = 0;
    boolean threadIsAlive = true;
    BlockingQueue<Packet> incomingPackets;

    public ReadingThread(InputStream inputStream, BlockingQueue<Packet> incomingPackets,
                          int device_id){
        this.incomingPackets = incomingPackets;
        this.inputStream = inputStream;

        this.device_id = device_id;
    }

    @Override
    public void run() {
        super.run();
        while(threadIsAlive){
            try {
                ObjectInput deSerializeObject = new ObjectInputStream(inputStream);
                Packet readPacket = (Packet) deSerializeObject.readObject();
                debug("[READING] GOT "+readPacket.toString(), INFO);
                incomingPackets.put(readPacket);

            }  catch(ClassNotFoundException exp){
                debug(exp.toString(), WARN);
            } catch(IOException exp){
                debug(exp.toString(), WARN);
                cancel();
            }catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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

    public void cancel(){
        debug("Closing Reading Thread of node " + device_id, INFO);
        threadIsAlive = false;
        Packet packet = new Packet(device_id, MainActivity.DEVICE_ID, TERMINATE, 0, new byte[1]);
        incomingPackets.add(packet);
//        try {
//            incomingPackets.put(packet);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
