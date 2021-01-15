package mzero.game;
import clojure.lang.Keyword;
public class MovePosition {
    public static long[] movePosition (long x, long y, Keyword dir, int boardSize) {
        long [] res = new long[2];
	switch (dir.getName()) {
	case "up":
	    res[0] = Math.floorMod((x-1), boardSize);
	    res[1] = y;
	    break;
	case "down":
	    res[0] = Math.floorMod((x+1), boardSize);
	    res[1] = y;
	    break;
	case "left":
	    res[0] = x;
	    res[1] = Math.floorMod((y-1), boardSize);;
	    break;
	case "right":
	    res[0] = x;
	    res[1] = Math.floorMod((y+1), boardSize);
	    break;
	}
	return res;
    }
}
