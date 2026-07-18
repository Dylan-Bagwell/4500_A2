# Reversi

## Authors
Dylan Bagwell

## SENG 4500_A2
- **Creation Date:** 3/10/2025
- **Last Modified:** 26/10/2025

## How to Compile
```
javac Reversi.java
```

## How to Run
```
java Reversi <BROADCAST ADDRESS> <PORT>
```
Example:
```
java Reversi 255.255.255.255 4500
```

## Assumptions Made by `player1()` and `player2()`

### Networking / Startup
- `gameSocket` is already connected and non-null before either player method is called.
- Input and output streams can be created from `gameSocket` and will block on `readLine()` as needed.
- The program is single-threaded for turn-taking; each side blocks waiting for the other.

### Turn Order
- Player 1 (Black) always moves first.
- Players strictly alternate: `player1` sends a move then waits for `player2`, and `player2` waits for `player1` then sends a move.

### Message Protocol Between Peers
- Move messages are sent as: `"MOVE:row,col"`
- Other control messages: `"PASS"`, `"ERROR"`, `"YOU WIN"`, `"YOU LOSE"`, `"DRAW"`
- Incoming messages are upper-cased before validation (some handling assumes uppercase).
- `checkMessage(...)` validates and normalizes incoming messages and may return `"ERROR"` or final game results.

### Move Input Format and Validation
- User input must be a comma-separated pair `"row,col"` (e.g., `"4,3"`).
- Each row/col must parse as an integer and be within the board bounds expected by `updateBoard()`.
- The code assumes input is pre-validated by `setValidMoves(...)` and that `updateBoard(row, col, color)` will correctly apply and validate the move.
- If no valid moves exist for the current color, the client sends `"PASS"` and calls `determineWinner()`, then exits.

### Game Logic Helpers
- `setValidMoves(int color)`, `hasValidMoves(int color)`, `updateBoard(int row, int col, int color)`
- `printBoard(...)`, `printvalidMoves(int player)`, `determineWinner()`
- `checkMessage(...)` can return either a control string (`PASS`/`ERROR`/your result) or a moved position string.

### Error Handling / Termination
- On any detected `ERROR` or unrecoverable exception, the program prints an error and calls `System.exit(0)`.
- Network interruptions or malformed messages are treated as fatal (ERROR path).

### Concurrency / User Experience
- Console reads are blocking.
- The code assumes the opponent follows the same protocol and will respond in a timely manner.

## Notes
- Ensure consistent indexing convention (0-based vs 1-based) between users and `updateBoard()`/`printBoard()`.
- Make sure both peers use the same message formats and capitalization rules.
