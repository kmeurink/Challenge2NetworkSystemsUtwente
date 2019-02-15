package protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

import client.*;
//Other implementation of sliding window protocol.
public class MyProtocolVersionK2 extends IRDTProtocol {

    // change the following as you wish:
    static final int HEADERSIZE=1;   // number of header bytes in each packet
    static final int DATASIZE=64;   // max. number of user data bytes in each packet
    
    private int seqNumber = 0;
    private int timeOutCount = 0;
    private int lastPacketSent;
    private int lastHeader = -1;
    private int packageCount = 0;
    private boolean allAcksReceived = false;
    
    //Sliding window protocol variables
    private static final int sendWindowSize = 3;
    private static final int receiveWindowSize = 3;
    private static final int maxSeqNum = 7;
    private Semaphore queuBlock = new Semaphore(sendWindowSize);

    //Sending side:
    private int lastFrameSent;
    private int lastAckReceived;
    //private Integer[][] sendQueu = new Integer[sendWindowSize][HEADERSIZE + DATASIZE]; //TODO turn into list?
    private List<Integer[]> sendQueu = new CopyOnWriteArrayList<Integer[]>();
    
    //Receiving side:
    private int largestAcceptableFrame;
    private int lastFrameReceived = -1;
    private int nextFrameExpected = 0;
    //private Integer[][] receivedQueu = new Integer[recieveWindowSize][HEADERSIZE + DATASIZE]; //TODO turn into list?
    private List<Integer[]> receivedQueu = new CopyOnWriteArrayList<Integer[]>();


    
    @Override
    public void sender() {
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        // keep track of where we are in the data
        int filePointer = 0;
        
        //Keep going while there is still content in the file.
        while(filePointer < fileContents.length || !allAcksReceived) { // && !this.sendQueu.isEmpty()
        	if(sendQueu.size() < sendWindowSize && filePointer < fileContents.length) {
                // create a new packet of appropriate size
                int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
                Integer[] pkt = new Integer[HEADERSIZE + datalen];
                
                // write the current sequence number into the header byte
                pkt[0] = seqNumber;
                
                // copy databytes from the input file into data part of the packet, i.e., after the header
                System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
                
                //TODO does it need to block ?
                // send the packet to the network layer
                getNetworkLayer().sendPacket(pkt);
                sendQueu.add(pkt);
                client.Utils.Timeout.SetTimeout(1000, this, seqNumber);
                System.out.println("Sent one packet with header="+pkt[0]);
                
                //Keep track of the last packet sent.
                lastPacketSent = seqNumber;
                this.lastFrameSent = seqNumber;
                seqNumber = (seqNumber + 1);// % maxSeqNum; temporary implementation of inifinite count
                filePointer += datalen;
        	}
            // schedule a timer for 1000 ms into the future, just to show how that works:
            //client.Utils.Timeout.SetTimeout(1000, this, seqNumber);

            // and loop and sleep; you may use this loop to check for incoming acks...
               try {
               	Thread.sleep(10);
               	Integer[] ackPacket = getNetworkLayer().receivePacket();
                  if (ackPacket != null) {
                	  for (Integer[] i : sendQueu) {
                		  if (i[0] == ackPacket[0]) {
                			  System.out.println("Acknowledgement received for packet: " + i[0]);
                			  sendQueu.remove(i);
                			  this.lastAckReceived = ackPacket[0];
                			  packageCount++;
                			  if (this.sendQueu.isEmpty()) {
                				  this.allAcksReceived = true;
                			  }
                        	}
                        }
                    }
                } catch (InterruptedException e) {
                }
        }
        System.out.println("Total file sent. Number of packets sent: " + (packageCount));
        client.Utils.Timeout.Stop();

    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int z=(Integer)tag;
        // handle expiration of the timeout:
        for (Integer[] i : sendQueu) {
        	if (i[0] == z) {
                System.out.println("Timer expired with tag=" + z);
                getNetworkLayer().sendPacket(i);
                client.Utils.Timeout.SetTimeout(1000, this, z);
        	} else {
                System.out.println("Timer expired but was handled");
        	}
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

            // if we indeed received a packet
            if (packet != null) {
            	//if (packet[0] > this.lastFrameReceived && packet[0] <= this.largestAcceptableFrame) {
                    //Acknowledge the received packet.
                	this.receivedQueu.add(packet);
                    System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0] );
            	//}
            	timeOutCount = 0;
                // tell the server.
            	if (packet[0] < nextFrameExpected) {
                    Integer[] ackPacket = {packet[0]};
                    getNetworkLayer().sendPacket(ackPacket);
                    System.out.println("Acknowledgement sent.");
            	}

                // and let's just hope the file is now complete
            } else{
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                    Thread.sleep(10);
                    timeOutCount++;
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
            // append the packet's data part (excluding the header) to the fileContents array, first making it larger
            //Only happens if it is a new packet.
            for (Integer[] i : this.receivedQueu) { //TODO better looping of queue
                if (i[0] == nextFrameExpected) {
                	int oldlength=fileContents.length;
                	int datalen= i.length - HEADERSIZE;
                	fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
                	System.arraycopy(i, HEADERSIZE, fileContents, oldlength, datalen);
                	//this.lastFrameReceived = packet[0];
                	this.nextFrameExpected = (nextFrameExpected + 1);// % this.maxSeqNum;
                	this.largestAcceptableFrame = (this.lastFrameReceived + this.receiveWindowSize)% this.maxSeqNum;
                	this.receivedQueu.remove(i);
                	System.out.println("Packet " + i[0] + " appended to file.");
                }
            }
            
            if (timeOutCount == 1000) {
                //if (this.receivedQueu.isEmpty()) {
                	stop = true;
                //}
            }
        }

        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }
}
