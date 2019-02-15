package client;

/**
 * 
 * @author Jaco ter Braak, Twente University
 * @version 03-01-2015
 *
 * Provides an unreliable packet transmission framework to the reliable data transfer protocol
 *
 * DO NOT EDIT
 */

public class NetworkLayer {
    DRDTChallengeClient client;
    
    /**
     * Constructs the network layer
     * @param client The challenge client to be used as transmission medium
     */
    public NetworkLayer(DRDTChallengeClient client){
        this.client = client;
    }
    
    /**
     * Send a packet through the unreliable medium
     * @param packet
     */
    public void sendPacket(Integer[] packet) throws IllegalArgumentException{
        client.sendPacket(packet);
    }
    
    /**
     * Receive a packet from the unreliable medium
     * @return The content of the packet as an array of Integers, or null if no packet was received
     */
    public Integer[] receivePacket(){
        return client.receivePacket();
    }

    /**
     * Retrieve the timer tick from the server
     */
    public int getTick() {
        return client.getTick();
    }
}
