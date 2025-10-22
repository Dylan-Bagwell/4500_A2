
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
import java.util.ArrayList;
import java.util.HashMap;

public class Reversi extends Thread {

    private static Socket gameSocket;
    private static final int WAIT_TIME = 5000; // wait 5 seconds for connection
    private static final int BOARD = 8; // board is 8 x 8
    private static final char BLANK = ' ';
    private static final char BLACK = 'B';
    private static final char WHITE = 'W';
    private static int port;// port passed in from console
    private static boolean playingGame = false;
    private static boolean TCPConnected = false;
    private static char[][] reversiBoard = new char[BOARD][BOARD];//Reversi Board
    private static String[][] player1Moves;
    private static String[][] player2Moves;
    private static final int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    public static InetAddress gameAddress;// ip address recieved
    public static String addressIp;//my address entered in the command line
    public static String response;
    public static String[] parts;
    public static HashMap<String, ArrayList<String>> moveMap = new HashMap<>();

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
            playingGame = true; // we are now playing the game
            player1();

        } else if (!TCPConnected) {
            System.out.println("[TCP] Could not establish TCP connection, continuing UDP broadcast...");
        }
    }

    public static void printBoard(char[][] reversiBoard) {
        String[] Top = {"   1  ", "2  ", "3  ", "4  ", "5  ", "6  ", "7  ", "8  "};
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
                if (reversiBoard[row][col] == 'x' || reversiBoard[row][col] == 'X') {
                    reversiBoard[row][col] = BLANK; // reset valid move markers
                }
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
        player1Moves = new String[BOARD][BOARD];
        player2Moves = new String[BOARD][BOARD];
        // Initialize the entire board with blanks
        for (int i = 0; i < BOARD; i++) {
            for (int j = 0; j < BOARD; j++) {
                reversiBoard[i][j] = BLANK;
            }
        }

        // Set up the initial Reversi configuration
        reversiBoard[middle - 1][middle - 1] = WHITE; // (3,3)
        reversiBoard[middle][middle] = WHITE;         // (4,4)
        reversiBoard[middle - 1][middle] = BLACK;     // (3,4)
        reversiBoard[middle][middle - 1] = BLACK;     // (4,3)

    }

    public static void updateBoard(int row, int col, char playerColor) {

        System.out.println("Updating board with move: " + row + "," + col + " for player: " + playerColor);

        reversiBoard[row - 1][col - 1] = playerColor; // place the player's piece
        
        // hashmap.get that position arraylist
        // hashmap <position> <arraylist>
        // for each position in list set to playerColor

        String key = "(" + row + "," + col + ")";
        ArrayList<String> posFlip = moveMap.get(key);//postions to be flipped
        if (posFlip != null) {
            for (String pos : posFlip) {
                // int flipRow = Integer.parseInt(pos.split(",")[0].substring(1)) - 1; // Adjust for 0-based index
                // int flipCol = Integer.parseInt(pos.split(",")[1].substring(0, pos.split(",")[1].length() - 1)) - 1; // Adjust for 0-based index

                //test code
                String[] coords = pos.replace("(", "").replace(")", "").split(",");
                int flipRow = Integer.parseInt(coords[0]) - 1; // Adjust for
                int flipCol = Integer.parseInt(coords[1]) - 1; // Adjust for 0-based index
                reversiBoard[flipRow][flipCol] = playerColor;
            }
        }
        printBoard(reversiBoard);
    }

    public static void clearValidMoves() {
        player1Moves = new String[BOARD][BOARD];
        player2Moves = new String[BOARD][BOARD];
    }

    public static void setValidMoves(char playerColor) {
        clearValidMoves();
        moveMap.clear(); //temp remove it doesnt work
        // Set valid moves logic here
        for (int i = 1; i < BOARD; i++) {
            for (int k = 1; k < BOARD; k++) {
                validMove(i, k, playerColor);
            }
        }
    }

    public static boolean validMove(int row, int col, char playerColor) {

        //for a color there is a horizontal vertical or diagonal line of opponent pieces
        if (row < 1 || row > BOARD || col < 1 || col > BOARD) {
            System.out.println("MOVE coordinates out of bounds.");
            return false;
        }
        if (playerColor == BLACK) {
            for (int i = 0; i < BOARD; i++) {
                boolean hasOpponentPieceBetween = false;
                for (int k = 0; k < BOARD; k++) {
                    for (int[] dir : directions) {
                        if (reversiBoard[i][k] != BLACK) {
                            continue; // Skip non-empty cells
                        }
                        int x = i + dir[0];// -1 coming from here
                        int y = k + dir[1];
                        if ((x == -1 || y == -1) || (x >= BOARD || y >= BOARD)) {
                            continue;
                        }
                        if (reversiBoard[x][y] == BLANK) {
                            continue;
                        }
                        if (reversiBoard[x][y] == WHITE) {
                            ArrayList<String> positions = new ArrayList<>();
                            positions.add("(" + (x + 1) + "," + (y + 1) + ")");
                            hasOpponentPieceBetween = true;
                            while (true) {
                                x += dir[0];
                                y += dir[1];
                                if ((x == -1 || y == -1) || (x >= BOARD || y >= BOARD)) {
                                    break;
                                }
                                if (reversiBoard[x][y] == BLACK) {
                                    hasOpponentPieceBetween = false;
                                    break;
                                } else if (reversiBoard[x][y] == BLANK) {
                                    if (hasOpponentPieceBetween) {
                                        player1Moves[x][y] = "(" + (x + 1) + "," + (y + 1) + ")";
                                        reversiBoard[x][y] = 'x'; // Mark valid move on board
                                        moveMap.put(player1Moves[x][y], positions);
                                    }
                                    hasOpponentPieceBetween = false;
                                    break;
                                } else if (reversiBoard[x][y] == WHITE) {
                                    // continue searching
                                    positions.add("(" + (x + 1) + "," + (y + 1) + ")");
                                    continue;
                                }
                                break;
                            }
                        } else {
                            hasOpponentPieceBetween = false;
                            continue; // No opponent pieces in between
                        }
                    }
                }
            }
        } else if (playerColor == WHITE) {
            for (int i = 0; i < BOARD; i++) {
                boolean hasOpponentPieceBetween = false;
                for (int k = 0; k < BOARD; k++) {
                    for (int[] dir : directions) {
                        if (reversiBoard[i][k] != WHITE) {
                            continue; // Skip non-empty cells
                        }
                        int x = i + dir[0];// -1 coming from here
                        int y = k + dir[1];
                        //System.out.println("Checking direction: " + dir[0] + "," + dir[1] + " from position: " + i + "," + k);
                        if ((x == -1 || y == -1) || (x >= BOARD || y >= BOARD)) {
                            continue;
                        }
                        if (reversiBoard[x][y] == BLANK) {
                            continue;
                        }
                        if (reversiBoard[x][y] == BLACK) {
                            ArrayList<String> positions = new ArrayList<>();
                            positions.add("(" + (x + 1) + "," + (y + 1) + ")");
                            hasOpponentPieceBetween = true;
                            while (true) {
                                x += dir[0];
                                y += dir[1];
                                if ((x == -1 || y == -1) || (x >= BOARD || y >= BOARD)) {
                                    break;
                                }
                                if (reversiBoard[x][y] == WHITE) {
                                    hasOpponentPieceBetween = false;
                                    break;
                                } else if (reversiBoard[x][y] == BLANK) {
                                    if (hasOpponentPieceBetween) {
                                        player2Moves[x][y] = "(" + (x + 1) + "," + (y + 1) + ")";
                                        reversiBoard[x][y] = 'X';
                                        moveMap.put(player1Moves[x][y], positions);
                                    }
                                    hasOpponentPieceBetween = false;
                                    break;
                                } else if (reversiBoard[x][y] == BLACK) {
                                    // continue searching
                                    positions.add("(" + (x + 1) + "," + (y + 1) + ")");
                                    continue;
                                }
                                break;
                            }
                        } else {
                            hasOpponentPieceBetween = false;
                            continue; // No opponent pieces in between
                        }
                    }
                }
            }
        }
        return true; // Placeholder
        //temp logic to test valid moves
        // if (row < 1 || row > BOARD || col < 1 || col > BOARD) {
        //     return false; // Out of bounds
        // }

        // int r = row - 1; // Convert to 0-based index
        // int c = col - 1;

        // // Target cell must be empty
        // if (reversiBoard[r][c] != BLANK) {
        //     return false;
        // }

        // char opp = (playerColor == BLACK) ? WHITE : BLACK;
        // ArrayList<String> positionsToFlip = new ArrayList<>();

        // // Check all 8 directions
        // for (int[] dir : directions) {
        //     int x = r + dir[0];
        //     int y = c + dir[1];
        //     boolean foundOpponent = false;
        //     ArrayList<String> tempPositions = new ArrayList<>();

        //     // Traverse in the current direction
        //     while (x >= 0 && x < BOARD && y >= 0 && y < BOARD && reversiBoard[x][y] == opp) {
        //         foundOpponent = true;
        //         tempPositions.add("(" + (x + 1) + "," + (y + 1) + ")");
        //         x += dir[0];
        //         y += dir[1];
        //     }

        //     // If we find a player's piece after opponent pieces, it's a valid move
        //     if (foundOpponent && x >= 0 && x < BOARD && y >= 0 && y < BOARD && reversiBoard[x][y] == playerColor) {
        //         positionsToFlip.addAll(tempPositions);
        //     }
        // }

        // // If we found valid positions to flip, update the moveMap and valid moves
        // if (!positionsToFlip.isEmpty()) {
        //     String moveKey = "(" + row + "," + col + ")";
        //     moveMap.put(moveKey, positionsToFlip);

        //     if (playerColor == BLACK) {
        //         player1Moves[r][c] = moveKey;
        //         reversiBoard[r][c] = 'x'; // Mark valid move on board
        //     } else {
        //         player2Moves[r][c] = moveKey;
        //         reversiBoard[r][c] = 'X'; // Mark valid move on board
        //     }
        //     return true;
        // }

        // return false;
    }

    public static void printvalidMoves(int player) {
        if (player == 1) {
            System.out.println("Player 1 Valid Moves: ");
            for (int i = 0; i < BOARD; i++) {
                for (int k = 0; k < BOARD; k++) {
                    if (player1Moves[i][k] != null) {
                        System.out.print(player1Moves[i][k] + " ");
                    }
                }
            }
            System.out.println();
        } else if (player == 2) {
            System.out.println("Player 2 Valid Moves: ");
            for (int i = 0; i < BOARD; i++) {
                for (int k = 0; k < BOARD; k++) {
                    if (player2Moves[i][k] != null) {
                        System.out.print(player2Moves[i][k] + " ");
                    }
                }
            }
            System.out.println();
        }

    }

    public static void player1() {
        //first move logic here
        int player = 1;
        System.out.println("You are Player 1 (Black), you make the first move!");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(gameSocket.getInputStream())); PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(gameSocket.getOutputStream())), true); BufferedReader console = new BufferedReader(new InputStreamReader(System.in));) {
            while (playingGame) {
                setValidMoves(BLACK);
                printBoard(reversiBoard);
                printvalidMoves(player);
                System.out.println("Enter your move row,col: ex) 4,3");
                System.out.print("Your Move: ");
                String input = console.readLine();
                int row = Integer.parseInt(input.split(",")[0]);
                int col = Integer.parseInt(input.split(",")[1]);
                //updateBoard(row, col, BLACK);
                if (validMove(row, col, BLACK)) {
                    updateBoard(row, col, BLACK);
                } else {
                    System.out.println("Invalid Move, try again.");
                    input = console.readLine();
                    row = Integer.parseInt(input.split(",")[0]);
                    col = Integer.parseInt(input.split(",")[1]);
                    while (!validMove(row, col, BLACK)) {
                        System.out.println("Invalid Move, try again.");
                        input = console.readLine();
                        row = Integer.parseInt(input.split(",")[0]);
                        col = Integer.parseInt(input.split(",")[1]);
                    }
                }
                out.println("MOVE:" + input);
                System.out.println("Sent:" + input);
                System.out.println("Wait:....");
                response = in.readLine();
                response = checkMessage(response.toUpperCase(), console, WHITE);
                if (response.equals("ERROR")) {
                    System.out.println(" ERROR has occured, exiting...");
                    out.println("ERROR");
                    System.exit(0);
                }
                System.out.println("PLAYER 2: " + response + "\n");
            }
        } catch (Exception e) {
            System.out.println("Error has occured in game, exiting..." + e.getMessage());
            System.exit(0);
        }

    }

    public static void player2() {
        int player = 2;

        //wait for move from player 1
        // try (BufferedReader in = new BufferedReader(new InputStreamReader(gameSocket.getInputStream())); PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(gameSocket.getOutputStream())), true); BufferedReader console = new BufferedReader(new InputStreamReader(System.in));) {
        //     while (playingGame) {
        //         //setValidMoves(WHITE);
        //         printBoard(reversiBoard);
        //         //printvalidMoves(player);
        //         System.out.println(" Wait for Player 1 move...\n");
        //         String response = in.readLine();
        //         response = checkMessage(response.toUpperCase(), console, BLACK);
        //         if (response.contains("ERROR")) {
        //             System.out.println(" ERROR has occured, exiting...");
        //             out.println("ERROR");
        //             System.exit(0);
        //         }
        //         System.out.println("PLAYER 1: " + response);
        //         setValidMoves(WHITE);
        //         printvalidMoves(player);
        //         //updateBoard(port, port, BLACK);
        //         printBoard(reversiBoard);
        //         System.out.println("Enter your move row,col: ex) 4,3");
        //         System.out.println("Your Move: ");
        //         String input = console.readLine();
        //         int row = Integer.parseInt(input.split(",")[0]);
        //         int col = Integer.parseInt(input.split(",")[1]);
        //         updateBoard(row, col, WHITE);
        //         if (validMove(row, col, WHITE)) {
        //             updateBoard(row, col, WHITE);
        //         } else {
        //             System.out.println("Invalid Move, try again.");
        //             input = console.readLine();
        //             if (validMove(row, col, WHITE)) {
        //                 updateBoard(row, col, WHITE);
        //             } else {
        //                 System.out.println("Invalid Move again, exiting...");
        //                 out.println("ERROR");
        //                 System.exit(0);
        //             }
        //         }
        //         out.println("MOVE:" + input);
        //         System.out.println("Sent MOVE:" + input);
        //         System.out.println(" Wait for Player 1\n");
        //     }
        // } catch (Exception e) {
        //     System.out.println("Error has occured in game, exiting...");
        //     System.exit(0);
        // }
        // Placeholder for player 2 logic
        try (BufferedReader in = new BufferedReader(new InputStreamReader(gameSocket.getInputStream())); PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(gameSocket.getOutputStream())), true); BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            while (playingGame) {

                //printBoard(reversiBoard);
                System.out.println("Waiting for Player 1's move...");
                response = in.readLine();
                response = checkMessage(response.toUpperCase(), console, BLACK); // Player 1's move is Black
                if (response.contains("ERROR")) {
                    System.out.println("ERROR has occurred, exiting...");
                    out.println("ERROR");
                    System.exit(0);
                }
                System.out.println("PLAYER 1: " + response);

                setValidMoves(WHITE); // Set valid moves for Player 2 (White)
                printBoard(reversiBoard);
                printvalidMoves(player);

                // Player 2 makes a move
                System.out.println("Enter your move row,col: ex) 4,3");
                System.out.print("Your Move: ");
                String input = console.readLine();
                int row = Integer.parseInt(input.split(",")[0]);
                int col = Integer.parseInt(input.split(",")[1]);
                // Validate and update the board for Player 2 (White)
                if (validMove(row, col, WHITE)) {
                    updateBoard(row, col, WHITE);
                } 
                // else {
                //     System.out.println("Invalid Move, try again.");
                //     input = console.readLine();
                //     row = Integer.parseInt(input.split(",")[0]);
                //     col = Integer.parseInt(input.split(",")[1]);
                //     while (!validMove(row, col, WHITE)) {
                //         System.out.println("Invalid Move, try again.");
                //         input = console.readLine();
                //         row = Integer.parseInt(input.split(",")[0]);
                //         col = Integer.parseInt(input.split(",")[1]);
                //     }
                //     updateBoard(row, col, WHITE);
                // }

                // Send the move to Player 1
                out.println("MOVE:" + input);
                System.out.println("Sent MOVE: " + input);
                System.out.println("Waiting for Player 1...\n");
            }
        } catch (Exception e) {
            System.out.println("Error has occurred in the game, exiting...");
            System.exit(0);
        }
    }

    public static int ranPort() {
        int gamePort = (int) ((Math.random() * 100) + 9000);
        return gamePort;
    }

    //check incoming message for validity
    public static String checkMessage(String message, BufferedReader console, char color) {
        if (message == null || message.isEmpty()) {
            System.out.println("Invalid message received.");
            return "ERROR";
        } else if (message.contains("MOVE:")) {
            parts = message.split(":");
            if (parts.length != 2) {
                System.out.println("Invalid MOVE format.");
                return "ERROR";
            }
            String movePart = parts[1];
            String[] moveCoords = movePart.split(",");
            if (moveCoords.length != 2) {
                System.out.println("Invalid MOVE coordinates.");
                return "ERROR";
            }
            try {
                int row = Integer.parseInt(moveCoords[0]);
                int col = Integer.parseInt(moveCoords[1]);
                System.out.println("Parsed MOVE coordinates: row=" + row + ", col=" + col);
                if (row < 1 || row > BOARD || col < 1 || col > BOARD) {
                    System.out.println("MOVE coordinates out of bounds.");
                    return "ERROR";
                }
                validMove(row, col, color);
                updateBoard(row, col, color);//sends move to update board
            } catch (NumberFormatException e) {
                System.out.println("Non-numeric MOVE coordinates.");
                return "ERROR";
            }
            return message; // valid MOVE message
        } else if (message.equals("PASS")) {
            return message;  // Opponent has no valid moves
        } else if (message.equals("YOU WIN")) {
            return message; // GAME OVER
        } else if (message.equals("YOU LOSE")) {
            return message; // GAME OVER
        } else if (message.equals("DRAW")) {
            return message; // GAME OVER
        } else if (message.equals("ERROR")) {
            return message; // Game will end
        } else {
            System.out.println("Unknown message type.");
            return "ERROR";
        }

    }

}

// arraylist positions <inputmove> <positions it takes when done>
// player moves
// get arraylist from hashmap at that position
// for each position 
// if white now black
// if black now white
