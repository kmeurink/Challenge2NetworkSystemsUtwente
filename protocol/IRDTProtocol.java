package protocol;

import client.ITimeoutEventHandler;
import client.NetworkLayer;

/**
 * 
 * @author Jaco ter Braak & Frans van Dijk, Twente University
 * @version 10-02-2016
 *
 * Specifies a data transfer protocol.
 *
 * DO NOT EDIT
 */
public abstract class IRDTProtocol implements ITimeoutEventHandler {

    private NetworkLayer networkLayer;
    private int fileID;
    
    /**
     * Run the protocol as sender. Called from the framework
     */
    public abstract void sender();

    /**
     * Run the protocol as receiver. Called from the framework
     */
    public abstract void receiver();
    
    /**
     * Sets the network layer implementation. This network layer is used for transmitting and receiving packets.
     * @param networkLayer the network layer to use for transmitting and receiving packets.
     */
    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }

    /**
     * Gets the network layer implementation to use for transmitting and receiving packets.
     * @return the network layer implementation to use for transmitting and receiving packets.
     */
    protected NetworkLayer getNetworkLayer() {
        return networkLayer;
    }

    /**
     * Sets the ID of the file to send/receive.
     * @param fileID the ID of the file to send/receive.
     */
    public void setFileID(int fileID) {
        this.fileID = fileID;
    }

    /**
     * @return the ID of the file to send/receive.
     */
    public int getFileID() {
        return fileID;
    }
}
