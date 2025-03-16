package server.games;

import Utilities.messages.ticTacToe.TttMoveResp;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class TicTacToeGame {
    private final Map<String, Map<String, String>> tttGames = new ConcurrentHashMap<>();
    private final Map<String, String> tttCurrentPlayer = new ConcurrentHashMap<>();

    /**
     * Initializes a new Tic-Tac-Toe game.
     *
     * @param player1 The username of the first player (X).
     * @param player2 The username of the second player (O).
     * @return The game key (e.g., "player1:player2").
     */
    public String initializeGame(String player1, String player2) {
        String gameKey = player1 + ":" + player2;
        Map<String, String> board = initializeTttBoard();
        tttGames.put(gameKey, board);
        tttCurrentPlayer.put(gameKey, player1); // Player 1 (X) starts
        return gameKey;
    }

    /**
     * Processes a move in the Tic-Tac-Toe game.
     *
     * @param gameKey The game key (e.g., "player1:player2").
     * @param player  The username of the player making the move.
     * @param row     The row of the move (0-2).
     * @param col     The column of the move (0-2).
     * @return A response indicating the result of the move.
     */
    public TttMoveResp processMove(String gameKey, String player, int row, int col) {
        Map<String, String> board = tttGames.get(gameKey);
        if (board == null) {
            return new TttMoveResp("ERROR", 12006); // Game not found
        }

        // Check if it's the player's turn
        String currentPlayer = tttCurrentPlayer.get(gameKey);
        if (!player.equals(currentPlayer)) {
            return new TttMoveResp("ERROR", 12009); // Not your turn
        }

        // Check if the cell is already occupied
        String cellKey = row + "," + col;
        if (!board.get(cellKey).isEmpty()) {
            return new TttMoveResp("ERROR", 12007); // Invalid move
        }

        // Update the board with the player's move
        String symbol = player.equals(currentPlayer) ? "X" : "O";
        board.put(cellKey, symbol);

        // Switch turns
        String nextPlayer = player.equals(currentPlayer) ? getOpponent(gameKey, player) : player;
        tttCurrentPlayer.put(gameKey, nextPlayer);

        // Check for a win or tie
        if (checkWin(board, symbol)) {
            return new TttMoveResp("WIN", 0);
        } else if (checkTie(board)) {
            return new TttMoveResp("TIE", 0);
        } else {
            return new TttMoveResp("OK", 0);
        }
    }

    /**
     * Gets the opponent's username for a given game.
     *
     * @param gameKey The game key (e.g., "player1:player2").
     * @param player  The username of the current player.
     * @return The username of the opponent.
     */
    private String getOpponent(String gameKey, String player) {
        String[] players = gameKey.split(":");
        return players[0].equals(player) ? players[1] : players[0];
    }

    /**
     * Initializes an empty Tic-Tac-Toe board.
     *
     * @return A map representing the empty board.
     */
    private Map<String, String> initializeTttBoard() {
        Map<String, String> board = new HashMap<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                board.put(row + "," + col, ""); // Empty cell
            }
        }
        return board;
    }

    /**
     * Checks if the current move results in a win.
     *
     * @param board  The current game board.
     * @param symbol The symbol to check ("X" or "O").
     * @return True if the move results in a win, false otherwise.
     */
    private boolean checkWin(Map<String, String> board, String symbol) {
        // Check rows, columns, and diagonals
        for (int i = 0; i < 3; i++) {
            // Check rows
            if (board.get(i + ",0").equals(symbol) && board.get(i + ",1").equals(symbol) && board.get(i + ",2").equals(symbol)) {
                return true;
            }
            // Check columns
            if (board.get("0," + i).equals(symbol) && board.get("1," + i).equals(symbol) && board.get("2," + i).equals(symbol)) {
                return true;
            }
        }
        // Check diagonals
        if (board.get("0,0").equals(symbol) && board.get("1,1").equals(symbol) && board.get("2,2").equals(symbol)) {
            return true;
        }
        if (board.get("0,2").equals(symbol) && board.get("1,1").equals(symbol) && board.get("2,0").equals(symbol)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the game has ended in a tie.
     *
     * @param board The current game board.
     * @return True if the game is a tie, false otherwise.
     */
    private boolean checkTie(Map<String, String> board) {
        // Check if all cells are filled
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (board.get(row + "," + col).isEmpty()) {
                    return false; // At least one cell is empty
                }
            }
        }
        return true; // All cells are filled
    }

    /**
     * Removes a game from the tttGames map.
     *
     * @param gameKey The game key (e.g., "player1:player2").
     */
    public void removeGame(String gameKey) {
        tttGames.remove(gameKey);
        tttCurrentPlayer.remove(gameKey);
    }
}