/*
 * Author: Dylan Bagwell
 * Student ID: c34342837
 * Course: SENG 4500 
 * Assignment: 2
 * Date: 03/10/2025
 * Description: This is a networked Reversi game that uses UDP to find opponents and TCP to play the game.
 * The game supports two players, where one player initiates a game request via UDP broadcast,
 * and the other player responds to establish a TCP connection.
 * The game board is displayed in the console, and players take turns making moves until the game concludes.
 * Valid moves are indicated on the board, and the game enforces Reversi rules for flipping pieces.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private static int UDPport;// port passed in from console
    private static boolean playingGame = false;
    private static boolean TCPConnected = false;
    private static char[][] reversiBoard = new char[BOARD][BOARD];//Reversi Board
    private static String[][] player1Moves;
    private static String[][] player2Moves;
    private static final int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};// for checking moves
    public static String addressIp;//my address entered in the command line
    public static String response;// response from opponent
    public static String[] parts;// split response parts
    public static HashMap<String, ArrayList<String>> moveMap = new HashMap<>();//stores valid moves and positions to be flipped

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("[Expected]: <BROADCAST ADDRESS> <PORT>");
            System.exit(1);
        }
        UDPport = Integer.parseInt(args[1]);

        System.out.println("Client: " + args[0] + " " + UDPport);

        addressIp = args[0];//passed in broadcast address

        //listen on udp port for a tcp port to play on to be player2
        while (!TCPConnected) {

            listen(UDPport); // listen for incoming UDP connections

            try {
                DatagramSocket UDPSock = new DatagramSocket();
                UDPSock.setSoTimeout(WAIT_TIME);// time socked out after 5 seconds
                UDPSock.setBroadcast(true);
                udpBroadcast(UDPSock, UDPport);
            } catch (Exception e) {
                System.out.println("CONNECTION LOOP: " + e.getMessage());
                System.exit(0);
            }
            // if no connection established then broadcast udp to find player1
        }

    }

    /* 
     *  @ parameters int port
     *  Listens for incoming UDP packets on the specified port to establish a TCP connection for the game
     */
    public static void listen(int port) {

        // Listening for incoming UDP connections
        System.out.println("Listening for UDP packets on port " + port + "...");
        try {

            DatagramSocket listenSock = new DatagramSocket(null);
            listenSock.setBroadcast(true);
            listenSock.setSoTimeout(WAIT_TIME);// time socked out after 5 seconds
            listenSock.setReuseAddress(true);
            listenSock.bind(new InetSocketAddress(port));
            byte[] recieve = new byte[1024];//byte buffer to hold message

            DatagramPacket udpPacket = new DatagramPacket(recieve, recieve.length);

            listenSock.receive(udpPacket);//get incoming packet

            String message = new String(udpPacket.getData(), 0, udpPacket.getLength());
            String[] parts = message.split(":");
            if (parts[0].equals("NEW GAME")) {
                int tcpPort = Integer.parseInt(parts[1]);// take player 1's tcp port
                String senderAddress = udpPacket.getAddress().getHostAddress();

                System.out.println("[UDP RECEIVED]: NEW GAME request from " + senderAddress + " on TCP port " + tcpPort);

                // Attempt to establish TCP connection
                gameSocket = new Socket(senderAddress, tcpPort);
                System.out.println("[TCP CONNECTED]: " + senderAddress + " on port " + tcpPort);
                listenSock.close();// close udp socket
                TCPConnected = true;// we have connected 
                playingGame = true; //we found a game to play
                newBoard(reversiBoard);
                // your player 2 so wait for p1 move
                printBoard(reversiBoard);
                System.out.println("You are Player 2 (White). Waiting for Player 1 (Black) to make a move...");
                player2();
            }

        } catch (SocketException e) {
            System.err.println("[UDP TIMEOUT]: UDP Timed out on " + port);
        } catch (Exception e) {
            System.err.println("[UDP]: " + e.getMessage());
        }
    }

    /* 
     *  @ parameters DatagramSocket UDPSock, int port
     *  Broadcasts a NEW GAME request via UDP on the specified port and waits for a TCP connection
     */
    public static void udpBroadcast(DatagramSocket UDPSock, int port) {
        System.out.println("[UDP BroadCasting]: Broadcasting NEW GAME request on port " + port);
        int random = ranPort();

        Thread UDPSend = new Thread(() -> {

            try {
                String send = "NEW GAME:" + random;

                DatagramPacket sendPack = new DatagramPacket(send.getBytes(), send.length(), InetAddress.getByName(addressIp),
                        port);

                System.out.println("Sending UDP Paket to " + port);
                while (!Thread.currentThread().isInterrupted() && !TCPConnected) {
                    UDPSock.send(sendPack);
                    Thread.currentThread().sleep(1000); // wait a second before resending stop flooding
                }

            } catch (Exception e) {
                System.err.println("Could not open UDP socket on port " + port);
            }

        });
        UDPSend.start();

        //
        try {

            ServerSocket tcpSock = new ServerSocket(random);
            tcpSock.setSoTimeout(WAIT_TIME);
            gameSocket = tcpSock.accept();//wait for connection
            System.out.println("[TCP CONNECTED]: " + addressIp + " on port " + random);
            TCPConnected = true;
        } catch (SocketException e) {
            System.err.println("[TCP SOCKET ERROR]: TCP Timed out on " + random);
            TCPConnected = false;
            UDPSend.interrupt();
        } catch (IOException e) {
            System.err.println("[TCP IO EXCEPTION]: Could not connect to " + addressIp + " on port " + random);
        }

        if (TCPConnected) { // if we are player 1

            //initialize game 
            newBoard(reversiBoard);
            playingGame = true; // we are now playing the game
            player1();

        } else if (!TCPConnected) {
            System.out.println("[TCP] Could not establish TCP connection, continuing UDP broadcast...");
        }
    }

    /* 
     *  @ parameters Char[][] reeversiBoard
     *  Prints the current state of the board to the console along with the current score for both players
     */
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
        System.out.print("CURRENT SCORE " + "BLACK: " + score(reversiBoard, 'B'));
        System.out.print(" WHITE: " + score(reversiBoard, 'W') + "\n");
        System.out.println();
    }

    /*
     *   @ parameters Char[][] , Char c
     *   Calculates and returns the score for the given color on the Reversi board
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

    /*
     *   @ parameters Char[][] reversiBoard
     *   Initializes a new Reversi board with the starting configuration
     */
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

        // //test code for end game logic
        // for (int i = 0; i < 5; i++) {
        //     for (int k = 0; k < 7; k++) {
        //         reversiBoard[i][k] = BLACK;
        //     }
        // }

        // for (int i = 5; i < 8; i++) {
        //     for (int k = 0; k < 7; k++) {
        //         reversiBoard[i][k] = WHITE;
        //     }
        // }

        // Set up the initial Reversi configuration
        reversiBoard[middle - 1][middle - 1] = WHITE; // (3,3)
        reversiBoard[middle][middle] = WHITE;         // (4,4)
        reversiBoard[middle - 1][middle] = BLACK;     // (3,4)
        reversiBoard[middle][middle - 1] = BLACK;     // (4,3)

    }

    /*
     *   @ parameters int row, int col, char playerColor
     *   Updates the Reversi board with the player's move and flips the opponent's pieces accordingly
     */
    public static void updateBoard(int row, int col, char playerColor) {
        
        System.out.println("Updating board with move: " + row + "," + col + " for player: " + playerColor);

        // Clear ALL valid move markers before updating
        for (int i = 0; i < BOARD; i++) {
            for (int j = 0; j < BOARD; j++) {
                if (reversiBoard[i][j] == 'x' || reversiBoard[i][j] == 'X') {
                    reversiBoard[i][j] = BLANK;
                }
            }
        }

        reversiBoard[row - 1][col - 1] = playerColor; // place the player's piece to 0 based index

        // hashmap.get that position arraylist
        // hashmap <position> <arraylist>
        // for each position in list set to playerColor
        String key = "(" + row + "," + col + ")";
        ArrayList<String> posFlip = moveMap.get(key);//postions to be flipped
        if (posFlip != null) {
            for (String pos : posFlip) {

                String[] coords = pos.replace("(", "").replace(")", "").split(",");//break down coords
                int flipRow = Integer.parseInt(coords[0]) - 1; // Adjust for
                int flipCol = Integer.parseInt(coords[1]) - 1; // Adjust for 0-based index
                
                //flip if it's the opponent's piece
            
                    reversiBoard[flipRow][flipCol] = playerColor;
                
                
            }
        }
        //printBoard(reversiBoard);
    }

    /* 
     *   Clears the valid move markers for both players
     */
    public static void clearValidMoves() {
        player1Moves = new String[BOARD][BOARD];
        player2Moves = new String[BOARD][BOARD];
    }

    /* 
     *   @ parameters char playerColor
     *   Sets the valid moves for the given player color on the Reversi board
     */
    public static void setValidMoves(char playerColor) {
        clearValidMoves();
        moveMap.clear();
        // Set valid moves logic here
        for (int i = 1; i <= BOARD; i++) {
            for (int k = 1; k <= BOARD; k++) {
                validMove(i, k, playerColor);
            }
        }
    }

    /* 
     *   @ parameters int row, int col, char playerColor
     *   Checks if the move at the specified row and column is valid for the given player color
     *   If valid, updates the moveMap and marks the valid move on the board
     */
    public static boolean validMove(int row, int col, char playerColor) {
        
        //for a color there is a horizontal vertical or diagonal line of opponent pieces
        if (row < 1 || row > BOARD || col < 1 || col > BOARD) {
            return false; // Out of bounds
        }

        int r = row - 1; // Convert to 0-based index
        int c = col - 1;

        // Target cell must be empty (don't check if it's a marker, check if it's TRULY blank or a marker)
        if (reversiBoard[r][c] != BLANK && reversiBoard[r][c] != 'x' && reversiBoard[r][c] != 'X') {
            return false; // Position has a piece already
        }

        
        char opp = (playerColor == BLACK) ? WHITE : BLACK;
        ArrayList<String> positionsToFlip = new ArrayList<>();

        // Check directions
        for (int[] dir : directions) {
            int x = r + dir[0];
            int y = c + dir[1];
            boolean foundOpponent = false;
            ArrayList<String> tempPositions = new ArrayList<>();
            // Traverse in the current direction
            while (x >= 0 && x < BOARD && y >= 0 && y < BOARD && reversiBoard[x][y] == opp) {
                foundOpponent = true;
                tempPositions.add("(" + (x + 1) + "," + (y + 1) + ")");
                x += dir[0];
                y += dir[1];
            }
            // If we find a player's piece after opponent pieces, it's a valid move
            if (foundOpponent && x >= 0 && x < BOARD && y >= 0 && y < BOARD && reversiBoard[x][y] == playerColor) {
                positionsToFlip.addAll(tempPositions);
            }
        }
        // If we found valid positions to flip, update the moveMap and valid moves
        if (!positionsToFlip.isEmpty()) {
            String moveKey = "(" + row + "," + col + ")";
            // Merge positions if this move was already found from another direction
            if (moveMap.containsKey(moveKey)) {
                moveMap.get(moveKey).addAll(positionsToFlip);
            } else {
                moveMap.put(moveKey, positionsToFlip);
            }
            if (playerColor == BLACK) {
                player1Moves[r][c] = moveKey;
                // Only mark if it's currently blank
                if (reversiBoard[r][c] == BLANK) {
                    reversiBoard[r][c] = 'x';
                }
            } else {
                player2Moves[r][c] = moveKey;
                // Only mark if it's currently blank
                if (reversiBoard[r][c] == BLANK) {
                    reversiBoard[r][c] = 'X';
                }
            }
            return true;
        }
        return false;
    }

    /* 
     *   @ parameters int player
     *   Prints the valid moves for the specified player to the console
     */
    public static void printvalidMoves(int player) {
        if (player == 1) {
            System.out.print("Player 1 Valid Moves: ");
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

    /* 
     *   Player 1's game loop
     */
    public static void player1() {
        //first move logic here
        int player = 1;
        System.out.println("You are Player 1 (Black), you make the first move!");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(gameSocket.getInputStream())); PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(gameSocket.getOutputStream())), true); BufferedReader console = new BufferedReader(new InputStreamReader(System.in));) {
            while (playingGame) {

                setValidMoves(BLACK);
                if (!hasValidMoves(BLACK)) {
                    System.out.println("No valid moves available. You must PASS your turn.");
                    out.println("PASS");
                    determineWinner();
                    System.exit(0);
                }
                printBoard(reversiBoard);
                printvalidMoves(player);

                System.out.println("Enter your move row,col: ex) 4,3");
                System.out.print("Your Move: ");
                String input = console.readLine();

                //Error checking before sending move to oppenent
                while (input == null || !input.contains(",")) {
                    System.out.println("Invalid Move, try again.");
                    System.out.print("Your Move: ");
                    input = console.readLine();
                }
                //players move
                int row = Integer.parseInt(input.split(",")[0]);
                int col = Integer.parseInt(input.split(",")[1]);

                //update board with player 1 move
                updateBoard(row, col, BLACK);
                //printBoard(reversiBoard);

                // Send move to player 2
                out.println("MOVE:" + input);
                System.out.println("Sent MOVE:" + input);
                System.out.println("Wait:....");
                response = in.readLine();
                response = checkMessage(response.toUpperCase(), console, WHITE);
                if (response.equals("ERROR")) {
                    System.out.println(" ERROR has occured, exiting..." + response);
                    out.println("ERROR");
                    System.exit(0);
                }else if (response.equals("YOU WIN") || response.equals("YOU LOSE") || response.equals("DRAW") || response.equals("PASS")) {
                    if (response.equals("PASS")) {
                        isGameOver();
                    }
                    System.out.println(response);
                    System.exit(0);
                }

                System.out.println("PLAYER 2 Sent: " + response);
            }
        } catch (Exception e) {
            System.out.println("Error has occured in game, exiting..." + e.getMessage());
            System.exit(0);
        }

    }

    /* 
     *   Player 2's game loop
     */
    public static void player2() {
        int player = 2;

        //wait for move from player 1
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
                } else if (response.equals("YOU WIN") || response.equals("YOU LOSE") || response.equals("DRAW") || response.equals("PASS")) {
                    if (response.equals("PASS")) {
                        isGameOver();
                    }
                    System.exit(0);
                }

                setValidMoves(WHITE); // Set valid moves for Player 2 (White)
                if (!hasValidMoves(WHITE)) {
                    System.out.println("No valid moves available. You must PASS your turn.");
                    out.println("PASS");
                    determineWinner();
                    System.exit(0);
                }
                printBoard(reversiBoard);
                System.out.println("PLAYER 1: " + response);
                printvalidMoves(player);

                // Player 2 makes a move
                System.out.println("Enter your move row,col: ex) 4,3");
                System.out.print("Your Move: ");
                String input = console.readLine();

                //Error checking before sending move to oppenent
                while (input == null || !input.contains(",")) {
                    System.out.println("Invalid Move, try again.");
                    System.out.print("Your Move: ");
                    input = console.readLine();
                }
                //players move
                int row = Integer.parseInt(input.split(",")[0]);
                int col = Integer.parseInt(input.split(",")[1]);

                //update board with player 2 move
                updateBoard(row, col, WHITE);
                printBoard(reversiBoard);

                // Send the move to Player 1
                out.println("MOVE:" + input);
                System.out.println("Sent MOVE: " + input);
                //System.out.println("Waiting for Player 1...\n");
            }
        } catch (Exception e) {
            System.out.println("Error has occurred in the game, exiting...");
            System.exit(0);
        }
    }

    /* 
     *   @ returns int
     *   Generates and returns a random port number between 9000 and 9100
     */
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
                System.out.println("Parsed MOVE coordinates: row = " + row + ", col = " + col);
                if (row < 1 || row > BOARD || col < 1 || col > BOARD) {
                    System.out.println("MOVE coordinates out of bounds.");
                    return "ERROR";
                }
                
                if(!validMove(row, col, color))
                {
                    System.out.println("Recieved Invalid MOVE.");
                    return "ERROR";
                }
                updateBoard(row, col, color);//sends move to update board
            } catch (NumberFormatException e) {
                System.out.println("Non-numeric MOVE: coordinates.");
                return "ERROR";
            }
            return message; // valid MOVE message
        } else if (message.equals("PASS")) {
            System.out.println("Opponent has passed their turn.");

            String decision = determineWinner();//returns case string to determine winner

            if (color == BLACK) {
                switch (decision) {
                    case "BLACK WINS":
                        System.out.println("YOU LOSE");
                        return "YOU WIN";
                    case "WHITE WINS":
                        System.out.println("YOU WIN");
                        return "YOU LOSE";
                    default:
                        System.out.println("ITS A DRAW!");
                        System.out.println("DRAW");
                        return "DRAW";
                }
            } else if (color == WHITE) {
                switch (decision) {
                    case "BLACK WINS":
                        System.out.println("YOU WIN");
                        return "YOU WIN";
                    case "WHITE WINS":
                        System.out.println("YOU LOSE");
                        return "YOU LOSE";
                    default:
                        System.out.println("DRAW");
                        return "DRAW";
                }
            }

        } else if (message.equals(
                "YOU WIN")) {
            determineWinner();
            return message; // GAME OVER
        } else if (message.equals(
                "YOU LOSE")) {
            determineWinner();// GAME OVER
        } else if (message.equals(
                "DRAW")) {
            determineWinner();// GAME OVER
        } else if (message.equals(
                "ERROR")) {
            System.out.println("Player sent ERROR: " + message);
            return message; // Game will end
        } else {
            System.out.println("Unknow message recieved: " + message);
            return "ERROR";
        }

        return message;
    }

    /*
     *   @ returns boolean
     *   Checks if the game is over by determining if the board is full or if both players have no valid moves
     */
    public static boolean isGameOver() {
        // Check if board is full
        boolean boardFull = true;
        for (int i = 0; i < BOARD; i++) {
            for (int j = 0; j < BOARD; j++) {
                if (reversiBoard[i][j] == BLANK || reversiBoard[i][j] == 'x' || reversiBoard[i][j] == 'X') {
                    boardFull = false;
                    break;
                }
            }
            if (!boardFull) {
                break;
            }
        }

        if (boardFull) {
            return true;
        } // Save the current board state
        char[][] tempBoard = new char[BOARD][BOARD];
        for (int i = 0; i < BOARD; i++) {
            for (int j = 0; j < BOARD; j++) {
                tempBoard[i][j] = reversiBoard[i][j];
            }
        }

        // Check if both players have no valid moves
        setValidMoves(BLACK);
        boolean blackHasMoves = hasValidMoves(BLACK);

        setValidMoves(WHITE);
        boolean whiteHasMoves = hasValidMoves(WHITE);

        // Restore the board state (remove markers added by setValidMoves)
        for (int i = 0; i < BOARD; i++) {
            for (int j = 0; j < BOARD; j++) {
                if (reversiBoard[i][j] == 'x' || reversiBoard[i][j] == 'X') {
                    reversiBoard[i][j] = tempBoard[i][j];
                }
            }
        }

        if (!blackHasMoves) {
            return true;
        } else if (!whiteHasMoves) {
            return true;

        }
        return !blackHasMoves && !whiteHasMoves;
    }

    /* 
     *   @ parameters char playerColor
     *   @ returns boolean
     *   Checks if the specified player has any valid moves available
     */
    public static boolean hasValidMoves(char playerColor) {
        if (playerColor == BLACK) {
            for (int i = 0; i < BOARD; i++) {
                for (int j = 0; j < BOARD; j++) {
                    if (player1Moves[i][j] != null) {
                        return true;
                    }
                }
            }
        } else {
            for (int i = 0; i < BOARD; i++) {
                for (int j = 0; j < BOARD; j++) {
                    if (player2Moves[i][j] != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /* 
     *   @ returns String
     *   Determines the winner of the game based on the current scores and prints the result
     */
    public static String determineWinner() {
        int blackScore = score(reversiBoard, BLACK);
        int whiteScore = score(reversiBoard, WHITE);
        
        System.out.println("\n=== GAME OVER ===");
        System.out.println("Final Score - BLACK: " + blackScore + " WHITE: " + whiteScore + "\n");

        if (blackScore > whiteScore) {
            System.out.println("BLACK WINS\n");
            return "BLACK WINS";
        } else if (whiteScore > blackScore) {
            System.out.println("WHITE WINS\n");
            return "WHITE WINS";
        } else {
            System.out.println("DRAW\n");
            return "DRAW";
        }
    }
}
