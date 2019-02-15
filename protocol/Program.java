package protocol;

import client.DRDTChallengeClient;
import client.NetworkLayer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Entry point of the program. Starts the client and links the used MAC
 * protocol.
 *
 * @author Jaco ter Braak & Frans van Dijk, Twente University
 * @version 12-02-2018
 */
public class Program {

    // Change to your group authentication token
    private static String groupToken = "e57d5faf-c342-449a-b7ad-6a79b901d118";

    // Choose ID of test file to transmit: 1, 2, 3, 4 or 5
    // Sizes in bytes are: 248, 2085, 6267, 21067, 53228
    private static int file = 1;

    // Change to your protocol implementation
    private static IRDTProtocol protocolImpl = new MyProtocolVersionK1();

    // Challenge server address
    // See the website for the hostname of the server
    private static String serverAddress = "networkingchallenges.ewi.utwente.nl";//"drdt.ns-staging.nl";//

    // Challenge server port
    private static int serverPort = 8002;

    /*
     *
     *
     *
     *
     *
     *
     *
     * DO NOT EDIT BELOW THIS LINE
     */
    public static void main(String[] args) {
        DRDTChallengeClient client = null;
        try {
            System.out.print("[FRAMEWORK] Starting client... ");

            // Create the client
            client = new DRDTChallengeClient(serverAddress, serverPort, groupToken);

            System.out.println("Done.");

            System.out.println("[FRAMEWORK] Press Enter to start the simulation as sender...");
            System.out.println("[FRAMEWORK] (Simulation will be started automatically as receiver " +
                                                "when the other client in the group issues the start command)");

            boolean startRequested = false;
            InputStream inputStream = new BufferedInputStream(System.in);
            while (!client.isSimulationStarted() && !client.isSimulationFinished()) {
                if (!startRequested && inputStream.available() > 0) {
                    // Request start as sender.
                    client.requestStart(file);
                    startRequested = true;
                }
                Thread.sleep(10);
            }

            System.out.println("[FRAMEWORK] Simulation started!");

            protocolImpl.setNetworkLayer(new NetworkLayer(client));
            protocolImpl.setFileID(client.getFileID());
            if (startRequested) {
                System.out.println("[FRAMEWORK] Running protocol implementation as sender...");
                protocolImpl.sender();
            } else {
                System.out.println("[FRAMEWORK] Running protocol implementation as receiver...");
                protocolImpl.receiver();
                client.sendChecksumOut();
            }

        } catch (IOException e) {
            System.out.println("[FRAMEWORK] Could not start the client, because: ");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("[FRAMEWORK] Operation interrupted.");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("[FRAMEWORK] Unexpected Exception: ");
            e.printStackTrace();
        } finally {
            if (client != null) {
                while(!client.isSimulationFinished()){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                System.out.print("[FRAMEWORK] Flushing output buffer... ");
                while (!client.isOutputBufferEmpty()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                System.out.println("Done.");
                System.out.print("[FRAMEWORK] Shutting down client... ");
                client.stop();
                System.out.println("Done.");
            }
            System.out.println("[FRAMEWORK] Terminating program.");
        }
    }
}
