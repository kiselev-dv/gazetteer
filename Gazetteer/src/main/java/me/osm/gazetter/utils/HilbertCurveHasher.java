package me.osm.gazetter.utils;

/**
 * Locality preserving hashing.
 * {@link http://blog.notdot.net/2009/11/Damn-Cool-Algorithms-Spatial-indexing-with-Quadtrees-and-Hilbert-Curves}
 * */
public class HilbertCurveHasher {

	private static final long FFFFFFFF = 4294967295l;
	private static final int A = 0;
	private static final int B = 1;
	private static final int C = 2;
	private static final int D = 3;
	
	private static final int hilbert_map[][][][] = new int[4][2][2][2];
	private static final int inverse_hilbert_map[][][] = new int[4][4][3];
	
	
	static {
		//	'a': {(0, 0): (0, 'd'), (0, 1): (1, 'a'), (1, 0): (3, 'b'), (1, 1): (2, 'a')}, 
		//  'b': {(0, 0): (2, 'b'), (0, 1): (1, 'b'), (1, 0): (3, 'a'), (1, 1): (0, 'c')}, 
		//	'c': {(0, 0): (2, 'c'), (0, 1): (3, 'd'), (1, 0): (1, 'c'), (1, 1): (0, 'b')}, 
		//	'd': {(0, 0): (0, 'a'), (0, 1): (3, 'c'), (1, 0): (1, 'd'), (1, 1): (2, 'd')}
		hilbert_map[A] = new int[][][]{{/*(0, 0):*/{0, D}, /*(0, 1):*/{1, A}}, {/*(1, 0):*/{3, B}, /*(1, 1):*/{2, A}}};
		hilbert_map[B] = new int[][][]{{/*(0, 0):*/{2, B}, /*(0, 1):*/{1, B}}, {/*(1, 0):*/{3, A}, /*(1, 1):*/{0, C}}};
		hilbert_map[C] = new int[][][]{{/*(0, 0):*/{2, C}, /*(0, 1):*/{3, D}}, {/*(1, 0):*/{1, C}, /*(1, 1):*/{0, B}}};
		hilbert_map[D] = new int[][][]{{/*(0, 0):*/{0, A}, /*(0, 1):*/{3, C}}, {/*(1, 0):*/{1, D}, /*(1, 1):*/{2, D}}};

		//	'a': {0: (0, 0, 'd'), 1: (0, 1, 'a'), 2: (1, 1, 'a'), 3: (1, 0, 'b')}, 
		//  'b': {0: (1, 1, 'c'), 1: (0, 1, 'b'), 2: (0, 0, 'b'), 3: (1, 0, 'a')}, 
		//	'c': {0: (1, 1, 'b'), 1: (1, 0, 'c'), 2: (0, 0, 'c'), 3: (0, 1, 'd')}, 
		//	'd': {0: (0, 0, 'a'), 1: (1, 0, 'd'), 2: (1, 1, 'd'), 3: (0, 1, 'c')}
		inverse_hilbert_map[A] = new int[][]{{0, 0, D}, {0, 1, A}, {1, 1, A}, {1, 0, B}};
		inverse_hilbert_map[B] = new int[][]{{1, 1, C}, {0, 1, B}, {0, 0, B}, {1, 0, A}};
		inverse_hilbert_map[C] = new int[][]{{1, 1, B}, {1, 0, C}, {0, 0, C}, {0, 1, D}};
		inverse_hilbert_map[D] = new int[][]{{0, 0, A}, {1, 0, D}, {1, 1, D}, {0, 1, C}};
	}
	
	/**
	 * Converts pair of coordinates into long HilbertCurve hash.
	 * with depth 16 (32 bits of resulting hash)
	 * */
	public static long encode(double x, double y) {
		int[] intCoordinates = intCoordinates(x, y);
		return encode(intCoordinates[0], intCoordinates[1]);
	}

	/**
	 * Converts long HilbertCurve hash into pair of coordinates. 
	 * */
	public static double[] decode(long hash) {
		long[] decode = decodeL(hash);
		return doubleCoordinates(decode[0], decode[1]);
	}
	
	/**
	 * Converts pair of coordinates into long HilbertCurve hash.
	 * with depth 16 (32 bits of resulting hash)
	 * */
	public static long encode(long x, long y) {
		return encode(x, y, 16);
	}

	/**
	 * Decodes hash into binary coordinates.
	 * */
	public static long[] decodeL(long hash) {
		return decode(hash, 16);
	}
	
	/**
	 * Encodes pair of long x,y with provided HilbertHash algorithm order.
	 * */
	public static long encode(long x, long y, int order) {
		
		int currentSquare = A;
		long position = 0;
		
		for(int i = order - 1; i >= 0; i--) {
			position <<= 2;
			int quadX = (x & (1 << i)) == 0 ? 0 : 1;
			int quadY = (y & (1 << i)) == 0 ? 0 : 1;
			
			int quadPosition = hilbert_map[currentSquare][quadX][quadY][0];
			currentSquare = hilbert_map[currentSquare][quadX][quadY][1];
			position |= quadPosition;
		}
		
		return position;
	}

	/**
	 * Decode long HilbertCurve hash into [x, y] long coordinates array
	 * with given HilbertCurve algorithm order
	 * */
	public static long[] decode(long hash, int order) {
		
		int position = A;

		long x = 0;
		long y = 0;

		for(int i = order - 1; i >=0; i--) {
			int p = (int) ((hash >> (i * 2)) & 3);
			
			int quadX = inverse_hilbert_map[position][p][0];
			int quadY = inverse_hilbert_map[position][p][1];
			position = inverse_hilbert_map[position][p][2];

			x = quadX == 0 ? x : (x | (1 << i));
			y = quadY == 0 ? y : (y | (1 << i));
		}
		
		return new long[]{x, y};
	}

	/**
	 * Convert double x, y into int [x, y]
	 * */
	private static int[] intCoordinates(double x, double y) {
		return new int[]{
			(int) (new Double((x + 180.0) * FFFFFFFF / 360.0).longValue() & FFFFFFFF), 
			(int) (new Double((y + 90.0) * FFFFFFFF / 180.0).longValue() & FFFFFFFF)
		};
	}

	/**
	 * Convert long x, y into double [x, y]
	 * */
	private static double[] doubleCoordinates(long x, long y) {
		
		return new double[]{
				(360.0d * x / 0xFFFFFFFF) - 180.0, 
				(180.0d * y / 0xFFFFFFFF) - 90.0 
		};
	}
	
}
