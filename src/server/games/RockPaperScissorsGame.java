package server.games;

import Utilities.messages.rockPaperScissor.RpsMoveResp;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class RockPaperScissorsGame {
    private final Map<String, String> playerToPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> playerMoves = new ConcurrentHashMap<>();

    /**
     * Starts a new Rock-Paper-Scissors game.
     *
     * @param player1 The username of the first player.
     * @param player2 The username of the second player.
     */
    public void startGame(String player1, String player2) {
        playerToPlayer.put(player1, player2);
        playerToPlayer.put(player2, player1);
    }

    /**
     * Processes a move in the Rock-Paper-Scissors game.
     *
     * @param player The username of the player making the move.
     * @param move   The move ("/r", "/p", or "/s").
     * @return A response indicating the result of the move.
     */
    public RpsMoveResp processMove(String player, String move) {
        String opponent = playerToPlayer.get(player);
        if (opponent == null) {
            return new RpsMoveResp("ERROR", 11005); // Player not in a game
        }

        // Record the player's move
        playerMoves.put(player, move);

        // Check if both players have made their moves
        if (playerMoves.containsKey(opponent)) {
            String opponentMove = playerMoves.get(opponent);
            String result = determineWinner(move, opponentMove);

            // Remove the game state
            playerToPlayer.remove(player);
            playerToPlayer.remove(opponent);
            playerMoves.remove(player);
            playerMoves.remove(opponent);

            return new RpsMoveResp(result, 0);
        } else {
            return new RpsMoveResp("OK", 0);
        }
    }

    /**
     * Determines the winner of a Rock-Paper-Scissors game.
     *
     * @param move1 The first player's move.
     * @param move2 The second player's move.
     * @return The result ("WIN", "LOSE", or "TIE").
     */
    private String determineWinner(String move1, String move2) {
        if (move1.equals(move2)) {
            return "TIE";
        }
        if ((move1.equals("/r") && move2.equals("/s")) ||
                (move1.equals("/s") && move2.equals("/p")) ||
                (move1.equals("/p") && move2.equals("/r"))) {
            return "WIN";
        }
        return "LOSE";
    }
}