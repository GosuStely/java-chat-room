package Utilities.messages.ticTacToe;

import java.util.Map;

public record TttResult(String winner, Map<String, String> board) {}