package me.osm.gazetter.diff.indx;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import me.osm.gazetter.diff.indx.ByteUtils.IdParts;

public class ByteUtils {
	
	public static final List<String> tailsTable = new ArrayList<>();
	
	public static final class IdParts {
		public String type;
		public String[] parts;
		public String tail;
	}
	
	public static IdParts parse(String id) {
		IdParts result = new IdParts();
		
		String[] split1 = StringUtils.splitByWholeSeparator(id, "--");
		
		if (split1.length >= 2) {
			result.tail = StringUtils.stripToNull(split1[1]);
			id = split1[0];
		}
		
		String[] parts = StringUtils.split(id, "-");
		result.type = parts[0];
		
		if (parts.length > 1) {
			result.parts = Arrays.copyOfRange(parts, 1, parts.length);
		}
		
		return result;
	}
	
	public static IdParts decode(ByteBuffer bb, String type) {
		IdParts result = new IdParts();
		result.type = type;
		
		((Buffer)bb).rewind();
		int length = bb.get();
		byte struct = bb.get();
		
		boolean hasTail = !(length % 4 == 0);
		
		List<String> parts = new ArrayList<String>();
		for (int i = 0; remainingWithoutTail(bb, hasTail) > 0; i++) {
			if (((struct >> i) & 1) == 0) {
				int p = bb.getInt();
				String hashString = Integer.toUnsignedString(p);
				String prefix = StringUtils.repeat('0', 10 - hashString.length());   
				parts.add(prefix + hashString);
			}
			else {
				long osmid = bb.getLong();
				
				long osmiddec = resetHighestByte(osmid);
				
				// highest bit set
				if (osmid < 0) {
					if((osmid & (1L << 61)) != 0) {
						ByteBuffer intpltnbb = ByteBuffer.allocate(8);
						intpltnbb.putLong(osmiddec);
						((Buffer)intpltnbb).position(2);
						
						byte hi = intpltnbb.get();
						byte low = intpltnbb.get();

						ByteBuffer intbb = ByteBuffer.allocate(4);
						((Buffer)intbb).position(2);
						
						intbb.put(hi).put(low);
						((Buffer)intbb).rewind();
						int hash = intpltnbb.getInt();
						String iPart = "i" + Integer.toUnsignedString(hash);

						int val = intbb.getInt();
						if(hi != (byte) -128 && low != 0) {
							iPart += "-" + val;
						}
						
						parts.add(iPart);
					}
					else {
						if((osmid & (1L << 62)) != 0) {
							parts.add("r" + osmiddec);
						}
						else {
							parts.add("w" + osmiddec);
						}
					}
				}
				else {
					parts.add("n" + osmiddec);
				}
			}
		}
		
		result.parts = parts.toArray(new String[parts.size()]);
		
		if (hasTail) {
			int tl = bb.get();
			if (tl == 0) {
				tl = bb.remaining();
			}
			if (tl == 2) {
				ByteBuffer tbb = ByteBuffer.allocate(4);
				((Buffer)tbb).position(2);
				tbb.put(bb);
				((Buffer)tbb).rewind();
				int tailTableIndex = tbb.getInt();
				result.tail = tailsTable.get(tailTableIndex);
			}
			else if (tl == 4) {
				int hash = bb.getInt();
				result.tail = StringUtils.replace(String.valueOf(hash), "-", "m");
			}
		}
		
		return result;
	}
	
	private static long resetHighestByte(long osmid) {
		osmid &= ~(1L << 63); 
		osmid &= ~(1L << 62);
		osmid &= ~(1L << 61);
		return osmid;
	}

	private static int remainingWithoutTail(ByteBuffer bb, boolean hasTail) {
		if (hasTail) {
			int r = bb.remaining();
			// 4 bytes tail
			if((r - 1) % 4 == 0) {
				return r - 5;
			}
			// 2 bytes tail
			else {
				return r - 3;
			}
		}
		else {
			return bb.remaining();
		}
	}

	public static ByteBuffer encode(IdParts id) {

		List<ByteBuffer> partsAsBytes = new ArrayList<>();
		if (id.parts != null) {
			Iterator<String> partsI = Arrays.asList(id.parts).iterator(); 
			while (partsI.hasNext()) {
				String p = partsI.next();
				if (isOsmId(p)) {
					ByteBuffer bb = osmIdAsBytes(p);
					partsAsBytes.add(bb);
				}
				else if (isInterpolation(p)) {
					String val = partsI.hasNext() ? partsI.next() : null;
					ByteBuffer bb = interpolationAsBytes(p, val);
					partsAsBytes.add(bb);
				}
				else if (isHash(p)) {
					ByteBuffer bb = hashAsBytes(p);
					partsAsBytes.add(bb);
				}
			}
		}
		
		int length = 0;
		int struct = 0;
		
		int structIndex = 0;
		for (ByteBuffer p : partsAsBytes) {
			length += p.capacity();
			if (p.capacity() == 8) {
				// set to 1
				struct |= (1 << structIndex);
			} 
			structIndex ++;
		}
		
		ByteBuffer tail = encodeTail(id);
		if (tail != null) {
			length += 1;
			
			ByteBuffer t = tail;
			((Buffer)t).rewind();
			tail = ByteBuffer.allocate(tail.capacity() + 1);
			tail.put((byte) t.capacity()).put(t);
			length += t.capacity();
		}
		
		byte structb = (byte) struct;
		
		ByteBuffer result = ByteBuffer.allocate(length + 2);
		result.put((byte)length);
		result.put(structb);
		
		for (ByteBuffer p : partsAsBytes) {
			result.put(p.array());
		}
		
		if (tail != null) {
			((Buffer)tail).rewind();
			result.put(tail);
		}
		
		return result;
	}

	private static ByteBuffer interpolationAsBytes(String string, String valString) {
		int hash = Integer.parseUnsignedInt(string.substring(1));
		
		ByteBuffer result = ByteBuffer.allocate(8);
		result.put((byte) 224);
		result.put((byte) 0);
		
		if (valString == null) {
			result.put((byte) -128);
			result.put((byte) 0);
		}
		else {
			int val  = Integer.parseInt(valString);
			result.put((byte) ((val >> 8) & 0xFF));
			result.put((byte) (val & 0xFF));
		}
		
		result.putInt(hash);
		
		((Buffer)result).rewind();
		return result;
	}

	private static boolean isInterpolation(String p) {
		return p.startsWith("i") && StringUtils.isNumeric(p.substring(1));
	}

	private static ByteBuffer hashAsBytes(String p) {
		p = StringUtils.replaceChars(p, "m", "-");
		Integer hash;
		if (p.contains("-")) {
			hash = Integer.parseInt(p);
		}
		else {
			hash = Integer.parseUnsignedInt(p);
		}
		ByteBuffer result = ByteBuffer.allocate(4);
		result.putInt(hash);
		return result;
	}

	private static boolean isHash(String p) {
		return StringUtils.containsOnly(p, "0123456789m");
	}

	private static ByteBuffer osmIdAsBytes(String p) {
		String numberString = p.substring(1);
		
		ByteBuffer result = ByteBuffer.allocate(8);
		result.putLong(Long.parseLong(numberString));
		
		if (StringUtils.startsWith(p, "r")) {
			byte head = result.get(0);
			result.put(0, (byte) (head | 192)); 
		}
		
		if (StringUtils.startsWith(p, "w")){
			byte head = result.get(0);
			result.put(0, (byte) (head | 128));
		}
		((Buffer)result).rewind();
		return result;
	}

	private static boolean isOsmId(String p) {
		boolean starts = StringUtils.startsWith(p, "r") || 
				StringUtils.startsWith(p, "n") || 
				StringUtils.startsWith(p, "w");
		
		return starts && StringUtils.isNumeric(p.substring(1));
	}

	private static ByteBuffer encodeTail(IdParts id) {
		
		ByteBuffer result = null;
		
		if (id.tail != null) {
			if (StringUtils.containsOnly(id.tail, "m0123456789")) {
				result = ByteBuffer.allocate(4);
				Integer tailInt = Integer.valueOf(StringUtils.replaceChars(id.tail, "m", "-"));
				result.putInt(tailInt);
			}
			else {
				result = ByteBuffer.allocate(2);
				int indx = tailsTable.indexOf(id.tail);
				if (indx < 0) {
					indx = tailsTable.size(); 
					tailsTable.add(id.tail);
				}
				result.put((byte) ((indx >> 8) & 0xFF));
				result.put((byte) (indx & 0xFF));
			}
		}
		
		return result;
	}
	
	public static String joinToId(IdParts idPartsDecoded) {
		String idDecoded = idPartsDecoded.type + "-" + StringUtils.join(idPartsDecoded.parts, '-');
		if (idPartsDecoded.tail != null) {
			idDecoded += "--" + idPartsDecoded.tail; 
		}
		return idDecoded;
	}

}
