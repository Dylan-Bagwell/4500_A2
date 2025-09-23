
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Reversi extends Thread {

    private static final int WAIT_TIME = 5000; // wait 5 seconds for connection
    private static final int BOARD = 8; // board is 8 x 8
    private static final String BLANK = " ";
    private static final char BLACK = 'B';
    private static final char WHITE = 'W';
    private static int port;// port passed in from console
    private static boolean playingGame = true;
    private static boolean UDPConnected = false;
    private static boolean TCPConnected = false;
    private static char[][] reversiBoard = new char[BOARD][BOARD];//Reversi Board
    public static InetAddress gameAddress;// ip address recieved

    public Reversi(String ipAddress, int port) //Constructor of the game Reversi
    {
        try (Socket gameSocket = new Socket(ipAddress, port)) {
            System.out.println("[CONNECTED]: " + ipAddress + " on port " + port);
        } catch (IOException e) {
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("[Expected]: <BROADCAST ADDRESS> <PORT>");
            System.exit(1);
        }
        port = Integer.parseInt(args[1]);
        //check port in range of 9000-9100
        while (!portRange(port)) {
            System.out.println("[PORT]: Must be within range 9000-9100");
            System.out.print("Enter valid port: ");
            port = Integer.parseInt(System.console().readLine());
        }
        System.out.println("Client: " + args[0] + " " + port);

        String addressIP = args[0];

        while (playingGame) {

            try (DatagramSocket UDPSock = new DatagramSocket(port)) {
                byte[] send = new byte[256]; //send buffer
                send = new String("NEWGAME:"+ port).getBytes();
                byte[] recieve = new byte[256];// recieve message buffer

                UDPSock.setSoTimeout(WAIT_TIME);//wait that 5 seconds

                while (!UDPSock.isClosed() && !UDPConnected) {

                    InetAddress address = InetAddress.getByName(addressIP);
                    DatagramPacket gamePacket = new DatagramPacket(send, send.length, address, port);//UDP NEWGAME packet to be sent out
                    UDPSock.send(gamePacket);
                    System.out.println("[SENT]: NEWGAME TO " + port);

                    try {
                        //wait for response
                        DatagramPacket udpPacket = new DatagramPacket(recieve, recieve.length);
                        UDPSock.receive(udpPacket);
                        String received = new String(udpPacket.getData(), 0, udpPacket.getLength());
                        if (received.contains("NEWGAME")) {
                            System.out.println("Message from: " + udpPacket.getAddress() + " : " + udpPacket.getPort());
                            gameAddress = udpPacket.getAddress();
                            port = udpPacket.getPort();
                            System.out.println("LET THE GAMES BEGIN");
                            UDPConnected = true;
                            //newBoard(reversiBoard);
                            //printBoard(reversiBoard);

                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("UDP Socket has timed out.......");
                    }
                }

            } catch (Exception e) {
                System.out.println("Match Making with UDP has Failed.....");
            }
            
            if(UDPConnected)
            {
                Thread TCPThread = new Thread(() -> {
                    try(Socket TCPSock = new Socket(gameAddress.getHostAddress(), port);
                    PrintWriter out = new PrintWriter(TCPSock.getOutputStream(),true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(TCPSock.getInputStream()));
                    ) {
                        String player1Responese = in.readLine();
                        System.out.println("Player1: " + player1Responese); //See what player1 responded with

                        out.println("MOVE: 2,3");
                        
                    } catch (IOException e) {
                        //System.out.println("[TCP]: Unable to connect to host, retry matchmaking");
                        System.out.println(e.getMessage());
                    }
                });
                TCPThread.start();
                try {
                    TCPThread.join();
                } catch (Exception e) {

                }
                
            }
        }
    }

    public static boolean portRange(int portNum) {
        boolean accept = false;
        if (portNum <= 9100 && portNum >= 9000) {
            accept = true;
        }
        return accept;
    }

    public static void printBoard(char[][] reversiBoard) {
        String[] Top = {"  1  ", "2  ", "3  ", "4  ", "5  ", "6  ", "7  ", "8  "};
        System.out.print("  ");
        System.out.printf("%s %s %s %s %s %s %s %s", Top[0], Top[1], Top[2], Top[3], Top[4], Top[5], Top[6], Top[7]);
        System.out.println();

        // Top border
        System.out.print("  +");
        for (int col = 0; col < 8; col++) {
            System.out.print("---+");
        }
        System.out.println();

        for (int row = 0; row < 8; row++) {
            System.out.printf("%3d |", row + 1);  // <-- padded row number
            for (int col = 0; col < 8; col++) {
                System.out.printf(" %c |", reversiBoard[row][col]);
            }
            System.out.println();

            // Row border
            System.out.printf("   +");
            for (int col = 0; col < 8; col++) {
                System.out.printf("---+");
            }
            System.out.println();
        }

        // display score
        System.out.print("CURRENT SCORE " + "BLACK:" + score(reversiBoard, 'B'));
        System.out.print(" WHITE:" + score(reversiBoard, 'W'));
        System.out.println();
    }

    /*
    *  @ parameters Char[][] , Char c
    *  Scans the matrix to find and count how many pieces choosen color has
     */
    private static int score(char[][] reversiBoard, char color) {
        int score = 0;
        for (int i = 0; i < BOARD; i++) {
            for (int k = 0; k < BOARD; k++) {
                if (reversiBoard[i][k] == color) {
                    score++;
                }
            }
        }
        return score;
    }

    public static void newBoard(char[][] reversiBoard) {
        int middle = BOARD / 2; //get the middle of the board

        for (int i = 0; i < BOARD; i++) {
            reversiBoard[i][i] = BLANK.charAt(0);//put spaces on baord
        }
        reversiBoard[middle - 1][middle - 1] = WHITE; // (3,3)
        reversiBoard[middle][middle] = WHITE;
        reversiBoard[middle - 1][middle] = BLACK;
        reversiBoard[middle][middle - 1] = BLACK;
    }

    public static int ranPort() {
        int gamePort = (int) ((Math.random() * 100) + 9000);
        return gamePort;
    }
}
