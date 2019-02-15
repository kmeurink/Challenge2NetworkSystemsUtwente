package protocol;


import java.util.Arrays;
import client.*;
//Alternating bit protocol.
public class MyProtocolVersionK3 extends IRDTProtocol {

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
    private int maxPackets;
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
            this.lastFrameSent = pkt[0];
            this.lastPacketSent = pkt;
    		filePointer += datalen;
            System.out.println("Sent one packet with header = " + pkt[0]);

            // schedule a timer, to ensure the packet is retransmitted if it never receives an ack.
            client.Utils.Timeout.SetTimeout(TIMEOUTDELAY, this, this.lastFrameSent);

            // Loop and sleep, waiting on an acknowledgement.
            boolean stop = false;
            while (!stop) {
                try {
                	//Check for packets
                    Integer[] packet = getNetworkLayer().receivePacket();
                    if (packet != null) {
                    	if (packet[0] == seqNum) {
                    		this.seqNum = (this.seqNum + 1) % 2;
                    		stop = true;
                            System.out.println("Acknowledgement received for tag = " + packet[0]);
                    	}
                    }
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
                if ((this.lastFrameReceived + 1) % 2 == packet[0]) {
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
