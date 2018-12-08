import java.util.PriorityQueue;

/**
 * Although this class has a history of several years, it is starting from a
 * blank-slate, new and clean implementation as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information and including
 * debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in  Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	/**
	 * Returns the encodings from a tree in an array
	 * @param root the tree
	 * @return encodings the array of encodings
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    codingHelper(root, "", encodings);
		return encodings;
	}
	
	/**
	 * Reads tree and adds paths to each leaf to array
	 * @param root root of specific subtree
	 * @param path path to node as 1's and 0's
	 * @param encodings array of encodings
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null && root.myRight == null) { // leaf node
			encodings[root.myValue] = path; // adds path to array
			return;
		} else {
			codingHelper(root.myLeft, path + "0", encodings); // adds 0 to path if left subtree
			codingHelper(root.myRight, path + "1", encodings); // adds 1 to path if right subtree
		}
	}

	/**
	 * Uses priority queue of HuffNodes to create Huffman trie
	 * @param counts array of frequencies of each 8-bit chunk
	 * @return Huffman trie
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		for(int i = 0; counts[i] > 0; i++) {
		    pq.add(new HuffNode(i, counts[i], null, null));
		}

		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
		    // create new HuffNode t with weight from
		    // left.weight+right.weight and left, right subtrees
		    pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/**
	 * Creates an array of frequencies for each 8-bit chunk from the input
	 * @param in the input 
	 * @return freq the array of frequencies
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		freq[PSEUDO_EOF] = 1;
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) 
				break;
			freq[bits]++;
		}
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		if (bits == -1) {
			throw new HuffException("illegal header starts with " + bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	/**
	 * Reads compressed bits and traverses tree
	 * @param root the root HuffNode
	 * @param in the input
	 * @param out the output
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) {
					current = current.myLeft;
				} else {
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break; // out of loop
					} else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}
	}
	
	/**
	 * Reads the first bit of an input to check if file is Huffman-coded
	 * @param in the input
	 * @return a HuffNode 
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		// read a single bit
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("bad input, no PSEUDO_EOF");
		}
		if (bit == 0) { // if internal node
			HuffNode left = readTreeHeader(in); // reads left subtree
			HuffNode right = readTreeHeader(in); // reads right subtree
			return new HuffNode(0, 0, left, right);
		} else { // if leaf node
			int value = in.readBits(BITS_PER_WORD + 1); // value stored in leaf
			return new HuffNode(value, 0, null, null);
		}
	}
}