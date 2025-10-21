
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class Reversi extends Thread {

    private static Socket gameSocket;
    private static final int WAIT_TIME = 5000; // wait 5 seconds for connection
    private static final int BOARD = 8; // board is 8 x 8
    private static final String BLANK = " ";
    private static final char BLACK = 'B';
    private static final char WHITE = 'W';
    private static int port;// port passed in from console
    private static int udpPort;
    private static boolean playingGame = false;
    private static boolean connected = false;
    private static boolean TCPConnected = false;
    private static char[][] reversiBoard = new char[BOARD][BOARD];//Reversi Board
    public static InetAddress gameAddress;// ip address recieved
    public static String addressIp;//my address entered in the command line
    public static String response; 
    public static String[] parts;

    // public Reversi(String ipAddress, int port) //Constructor of the game Reversi
    // {
    //     try (Socket gameSocket = new Socket(ipAddress, port)) {
    //         System.out.println("[CONNECTED]: " + ipAddress + " on port " + port);
    //     } catch (IOException e) {
    //     }
    // }
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

        addressIp = args[0];
        //listen on udp port for a tcp port to play on to be player2
        listen(port); // listen for incoming connections
        if (!TCPConnected) {
            try {
                DatagramSocket UDPSock = new DatagramSocket(null);
                UDPSock.setSoTimeout(WAIT_TIME);// time socked out after 5 seconds
                UDPSock.setBroadcast(true);
                udpBroadcast(UDPSock, port);
            } catch (Exception e) {
                System.exit(0);
            }
        } // if no connection established then broadcast udp to find player1

    }

    public static boolean portRange(int portNum) {
        boolean accept = false;
        if (portNum <= 9100 && portNum >= 9000) {
            accept = true;
        }
        return accept;
    }

    public static void tcpConnection(int port) {
        try {
            ServerSocket tcpSock = new ServerSocket(port);
            tcpSock.setSoTimeout(WAIT_TIME);
            gameSocket = tcpSock.accept();//wait for connection
            System.out.println("[TCP CONNECTED]: " + addressIp + " on port " + port);
            TCPConnected = true;
        } catch (SocketException e) {
            System.err.println("[TCP SOCKET ERROR]: TCP Timed out on " + port);
            TCPConnected = false;
        } catch (IOException e) {
            System.err.println("[TCP IO EXCEPTION]: Could not connect to " + addressIp + " on port " + port);
        }

    }

    public static void listen(int port) {
        // Listening for incoming TCP connections
        try {
            DatagramSocket listenSock = new DatagramSocket(null);
            listenSock.setSoTimeout(WAIT_TIME);// time socked out after 5 seconds
            byte[] recieve = new byte[1024];//byte buffer to hold message
            listenSock.setReuseAddress(true);
            listenSock.bind(new java.net.InetSocketAddress(port));
            DatagramPacket udpPacket = new DatagramPacket(recieve, recieve.length);
            listenSock.receive(udpPacket);

            String message = new String(udpPacket.getData(), 0, udpPacket.getLength());
            String[] parts = message.split(":");
            if (parts[0].equals("NEW GAME")) {
                int tcpPort = Integer.parseInt(parts[1]);// take player 1's tcp port
                String senderAddress = udpPacket.getAddress().getHostAddress();

                System.out.println("[UDP RECEIVED]: NEW GAME request from " + senderAddress + " on TCP port " + tcpPort);
                // Attempt to establish TCP connection
                gameSocket = new Socket(senderAddress, tcpPort);
                System.out.println("[TCP CONNECTED]: " + senderAddress + " on port " + tcpPort);

                TCPConnected = true;// we have connected 
                playingGame = true; //we found a game to play
                newBoard(reversiBoard);
                printBoard(reversiBoard);
                // your player 2 so wait for p1 move
                System.out.println("You are Player 2 (White). Waiting for Player 1 (Black) to make a move...");
                player2();
            }

        } catch (SocketException e) {
            System.err.println("[UDP ERROR]: UDP Timed out on " + port);
        } catch (Exception e) {
            System.err.println("[UDP ERROR]: Could not open UDP socket on port " + port);
        }
    }

    public static void udpBroadcast(DatagramSocket UDPSock, int port) {
        System.out.println("UDP BroadCasting");
        int random = ranPort();

        Thread UDPSend = new Thread(() -> {

            try {
                String send = "NEW GAME:" + random;

                DatagramPacket sendPack = new DatagramPacket(send.getBytes(), send.length(), InetAddress.getByName(addressIp),
                        port);

                System.out.print("Sending UDP Paket to " + port);
                while (!Thread.currentThread().isInterrupted() && !TCPConnected) {
                    UDPSock.send(sendPack);
                }

            } catch (Exception e) {
                System.err.println("Could not open UDP socket on port " + port);
            }
        });
        UDPSend.start();

        tcpConnection(random);

        if (TCPConnected) { // if we are player 1
            UDPSend.interrupt();
            try {
                UDPSend.join();
            } catch (InterruptedException e) {
                System.out.println("Could not join UDP thread.");
            }

            //initialize game 
            newBoard(reversiBoard);
            printBoard(reversiBoard);
            playingGame = true; // we are now playing the game
            player1();

        } else if (!TCPConnected) {
            System.out.println("[TCP] Could not establish TCP connection, continuing UDP broadcast...");
        }
    }

    public static void printBoard(char[][] reversiBoard) {
        String[] Top = {"  1  ", "2  ", "3  ", "4  ", "5  ", "6  ", "7  ", "8  "};
        System.out.print("  ");
        System.out.printf("%s %s %s %s %s %s %s %s", Top[0], Top[1], Top[2], Top[3], Top[4], Top[5], Top[6], Top[7]);
        System.out.println();

        // Top border
        System.out.print("   +");
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

        // Initialize the entire board with blanks
        for (int i = 0; i < BOARD; i++) {
            for (int j = 0; j < BOARD; j++) {
                reversiBoard[i][j] = BLANK.charAt(0);
            }
        }

        // Set up the initial Reversi configuration
        reversiBoard[middle - 1][middle - 1] = WHITE; // (3,3)
        reversiBoard[middle][middle] = WHITE;         // (4,4)
        reversiBoard[middle - 1][middle] = BLACK;     // (3,4)
        reversiBoard[middle][middle - 1] = BLACK;     // (4,3)
    }

    public static void player1() {
        //first move logic here
        System.out.println("You are Player 1 (Black), you make the first move!");

        try(BufferedReader in = new BufferedReader(new InputStreamReader(gameSocket.getInputStream()));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(gameSocket.getOutputStream())), true);
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        ) {
            while (playingGame) {
                printBoard(reversiBoard);
                System.out.println("Enter your move row,col: ex) 4,3");
                System.out.print("Your Move: ");
                String move = console.readLine();
                out.println("MOVE:" + move);
                System.out.println("Sent:" + move);
                System.out.print("Wait:....");
                response = in.readLine();
                System.out.println("Received: " + response);
            }
        } catch (Exception e) {
            System.out.println("Error has occured in game, exiting...");
            System.exit(0);
        }

    }

    public static void player2() {
        System.out.println("Waiting for Player 1 (Black) to make a move...");
        //wait for move from player 1
        try(BufferedReader in = new BufferedReader(new InputStreamReader(gameSocket.getInputStream()));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(gameSocket.getOutputStream())), true);
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        ) {
            while (playingGame) {
                printBoard(reversiBoard);
                System.out.print(" Wait....");
                String response = in.readLine();
                System.out.println("Received: " + response);
                System.out.println("Enter your move row,col: ex) 4,3");
                System.out.print("Your Move: ");
                String command = console.readLine();
                out.println("MOVE:" + command);
                System.out.println("Sent MOVE:" + command);
                System.out.println(" Wait for Player 2");

            }
        } catch (Exception e) {
            System.out.println("Error has occured in game, exiting...");
            System.exit(0);
        }
    }

    public static int ranPort() {
        int gamePort = (int) ((Math.random() * 100) + 9000);
        return gamePort;
    }

    //check incoming message for validity
    public static void checkMessage(String message) {

    }

}
