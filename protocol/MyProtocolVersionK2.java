package protocol;


import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import client.*;

//Optimizing sliding window protocol
public class MyProtocolVersionK2 extends IRDTProtocol {

    static final int HEADERSIZE=1;   // number of header bytes in each packet
    static final int DATASIZE=128;   // max. number of user data bytes in each packet
    static final int TIMEOUTDELAY=700;   // time in ms before a packet is retransmitted.
    private int seqNum = 0;
    private int lastFrameSent = 0;
    private Integer[] lastPacketSent;
    private int lastFrameReceived = -1;
    private int tryCount = 0;
    private int packetCount = 0;
    private int packetsSent = 0;
    private int listenCount = 0;
    private int maxPackets;
    
  //Sliding window protocol variables
    private static final int sendWindowSize = 10;
    private static final int receiveWindowSize = 10;
    private static final int maxSeqNum = 21;

    //Sending side:
    private int lastAckReceived;
    //private Integer[][] sendQueu = new Integer[sendWindowSize][HEADERSIZE + DATASIZE]; //TODO turn into list?
   // private List<Integer[]> sendPacketQueu = new CopyOnWriteArrayList<Integer[]>();
    //private List<Integer> sendSeqQueu = new CopyOnWriteArrayList<Integer>();
    private ConcurrentHashMap<Integer, Integer[]> sendPacketQueu = new ConcurrentHashMap<Integer, Integer[]>();
    
    //Receiving side:
    private int largestAcceptableFrame;
    private int nextFrameExpected = 0;
    //private Integer[][] receivedQueu = new Integer[recieveWindowSize][HEADERSIZE + DATASIZE]; //TODO turn into list?
    private List<Integer[]> receivedQueu = new CopyOnWriteArrayList<Integer[]>();
    
    
    @Override
    public void sender() {
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());
        this.maxPackets = (int)Math.ceil(fileContents.length / DATASIZE) + 1;

        // keep track of where we are in the data
        int filePointer = 0;
        
        //Ensure all packets are sent before stopping.
        for (int i = 0; i < maxPackets; i++) {
            // create a new packet of appropriate size
        	if(this.sendPacketQueu.size() < this.sendWindowSize) {
                int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
                Integer[] pkt = new Integer[HEADERSIZE + datalen];
                packetCount++;
                
                // write sequence number into the header byte
                pkt[0] = seqNum;    
                
                // copy databytes from the input file into data part of the packet, i.e., after the header
                System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);

                // send the packet to the network layer
                getNetworkLayer().sendPacket(pkt);
                packetsSent++;
                
                //Remember the seq number of the last frame sent and keep the packet in memory.
                this.lastFrameSent = pkt[0];
                this.sendPacketQueu.put(pkt[0], pkt);
                
        		filePointer += datalen;
                System.out.println("Sent one packet with header = " + pkt[0]);

                // schedule a timer, to ensure the packet is retransmitted if it never receives an ack.
                client.Utils.Timeout.SetTimeout(TIMEOUTDELAY, this, this.lastFrameSent);
                
                //Increase the seq number for the next packet.
        		this.seqNum = (this.seqNum + 1) % maxSeqNum;
        	}

            // Loop and sleep, waiting on an acknowledgement.
            boolean stop = false;
            while (!stop || listenCount < 10) {
                try {
                	//Check for packets
                    Integer[] packet = getNetworkLayer().receivePacket();
                    if (packet != null) {
                    	listenCount = 0;
                    	if (this.sendPacketQueu.containsKey(packet[0])) {
                    		this.sendPacketQueu.remove(packet[0]);
                    		this.lastAckReceived = packet[0];
                    		stop = true;
                            System.out.println("Acknowledgement received for tag = " + packet[0]);
                    	}
                    }
                    listenCount++;
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        	
        }
        System.out.println("Total packets sent: " + packetsSent);
        System.out.println("Individual packets sent: " + this.packetCount);
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int z=(Integer)tag;
        // handle expiration of the timeout:
        if (this.seqNum == z) {
        	//Retransmit packet and restart timer.
            getNetworkLayer().sendPacket(this.lastPacketSent);
            packetsSent++;
            client.Utils.Timeout.SetTimeout(TIMEOUTDELAY, this, z);
            System.out.println("Acknowledgement not received, tag = " + z);
        }
    }

    @Override
    public void receiver() {
        System.out.println("Receiving...");

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];

        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {

            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();
            if (tryCount >= 500) {
                //Try 500 times to find a packet and let's just hope the file is now complete.
                stop=true;
            }
            // if we indeed received a packet
            if (packet != null) {

                // tell the user
                System.out.println("Received packet, length=" + packet.length+"  first byte=" + packet[0] );
                
                //If the file has not been received before.
                if ((this.lastFrameReceived + 1) % maxSeqNum == packet[0]) {
                	tryCount = 0;
                    // append the packet's data part (excluding the header) to the fileContents array, first making it larger
                    System.out.println("Correct packet received.");
                    int oldlength=fileContents.length;
                    int datalen= packet.length - HEADERSIZE;
                    fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
                    System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);
                    this.lastFrameReceived = packet[0];
                }
                //Send acknowledgement to server.
                Integer[] ackPacket = {packet[0]};
                getNetworkLayer().sendPacket(ackPacket);

            } else{
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                	tryCount++;
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }

        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }
}
