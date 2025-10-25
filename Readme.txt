#Authors
Dylan Bagwell C3432837 

# SENG 4500_A2

-CREATION DATE: 3/10/2025
-LAST MODIFIED:26/10/2025


#How to Compile

javac Reversi.java

#How to run

java Reversi <BROADCAST ADDRESS> <PORT>

-ex java Reversi 255.255.255.255 4500

#Assumptions made by player1() and player2()

- Networking / startup
    - gameSocket is already connected and non-null before either player method is called.
    - Input and output streams can be created from gameSocket and will block on readLine() as needed.
    - The program is single-threaded for turn-taking; each side blocks waiting for the other.

- Turn order
    - Player 1 (Black) always moves first.
    - Players strictly alternate: player1 sends a move then waits for player2 and player2 waits for player1 then sends a move.

- Message protocol between peers
    - Move messages are sent as: "MOVE:row,col"
    - Other control messages: "PASS", "ERROR", "YOU WIN", "YOU LOSE", "DRAW"
    - Incoming messages are upper-cased before validation (some handling assumes uppercase).
    - checkMessage(...) validates and normalizes incoming messages and may return "ERROR" or final game results.

- Move input format and validation
    - User input must be a comma-separated pair "row,col" (e.g., "4,3").
    - Each row/col must parse as an integer and be within the board bounds expected by updateBoard().
    - The code assumes input is pre-validated by setValidMoves(...) and that updateBoard(row,col,color) will correctly apply and validate the move.
    - If no valid moves exist for the current color, the client sends "PASS" and calls determineWinner(), then exits.

- Game logic helpers
    - setValidMoves(int color), hasValidMoves(int color), updateBoard(int row,int col,int color) 
    - printBoard(...), printvalidMoves(int player), determineWinner()
    - checkMessage(...) can return either a control string (PASS/ERROR/your result) or a moved position string.

- Error handling / termination
    - On any detected ERROR or unrecoverable exception, the program prints an error and calls System.exit(0).
    - Network interruptions or malformed messages are treated as fatal (ERROR path).

- Concurrency/user experience
    - Console reads are blocking.
    - The code assumes the opponent follows the same protocol and will respond in a timely manner.

Notes:
- Ensure consistent indexing convention (0-based vs 1-based) between users and updateBoard()/printBoard().
- Make sure both peers use the same message formats and capitalization rules.
