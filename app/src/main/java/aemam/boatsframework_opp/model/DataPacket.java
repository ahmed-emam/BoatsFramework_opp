package aemam.boatsframework_opp.model;

import java.io.Serializable;

/**
 * Created by ahmedemam on 12/28/15.
 */
public class DataPacket implements Serializable {
    
    String filename;
    int fileLength;
    byte[] data;
    public DataPacket(String filename, int fileLength, byte[] data) {
        this.filename = filename;
        this.fileLength = fileLength;
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public int getFileLength() {
        return fileLength;
    }

    public String getFilename() {
        return filename;
    }

    public String toString(){
        return "| Data Packet | "+filename+" | "+fileLength+" |";
    }
}
