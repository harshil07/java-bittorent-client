/*
 *  RUBTClient is a BitTorrent client written at Rutgers University for 
 *  instructional use.
 *  Copyright (C) 2008  Robert S. Moore II
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * 
 * @author Robert Moore
 *
 */
public final class Bencoder2 
{
    private static final int INVALID = -1;
    private static final int DICTIONARY = 0;
    private static final int INTEGER = 1;
    private static final int STRING = 2;
    private static final int LIST = 3;
    
    public static void main(String[]args) throws Exception
    {
        if(args.length != 1)
        {
            System.err.println("Please specify a test file to bdecode.");
            System.exit(1);
        }
        
        File test_file = new File(args[0]);
        if(!test_file.canRead())
        {
            System.err.println("Cannot read file " + test_file.getCanonicalPath());
            System.exit(1);
        }
        
        long file_length = test_file.length();
        if(file_length > ((long)Integer.MAX_VALUE & 0xFFFFFFFF))
        {
            System.err.println("File too big!");
            System.exit(1);
        }
        byte[] file_bytes = new byte[(int)file_length];
        
        FileInputStream in = new FileInputStream(test_file);
        
        byte[] buffer = new byte[4096];
        int bytes_read = 0;
        int k = 0;
        while((k = in.read(buffer, 0, buffer.length)) > 0)
        {
            System.arraycopy(buffer,0,file_bytes,bytes_read,k);
            bytes_read += k;
            if(bytes_read == file_length)
                break;
        }
        
        if(bytes_read < file_length)
        {
            System.err.println("Could not read entire file. (" + bytes_read + "/" + file_length + "B)");
            System.exit(1);
        }
        
        ToolKit.print(decode(file_bytes));
//        HashMap<ByteBuffer, Object> map = (HashMap<ByteBuffer,Object>)decode(file_bytes);
        
       // ToolKit.print(ByteBuffer.wrap(file_bytes));
        
        HashMap<ByteBuffer,Object> torrent_dictionary = (HashMap<ByteBuffer, Object>)decode(file_bytes);
        HashMap<ByteBuffer,Object> info_dictionary = (HashMap<ByteBuffer,Object>)torrent_dictionary.get(ByteBuffer.wrap(new byte[] {(byte)'i',(byte)'n',(byte)'f',(byte)'o'}));
        ToolKit.print(info_dictionary);
        
        System.out.println("\n\nInfo Bytes");
        ByteBuffer info_bytes = getInfoBytes(file_bytes);
        ToolKit.print(info_bytes);
        
        String s = "piece length";
        byte[] bytes = s.getBytes();
        Charset c = Charset.forName("UTF-8");
        CharBuffer cbuf = CharBuffer.wrap(s);
        
        ByteBuffer buf = c.encode(cbuf);
        
        buf = (ByteBuffer)torrent_dictionary.get(buf);
        System.out.println(buf);
        //        Integer integer = new Integer(-12345);
//        byte[] string = new byte[]{'a','x','e','7','0'};
//        HashMap dictionary = new HashMap();
//        dictionary.put(new byte[] {'s','p','a','m'}, new Integer(5));
//        dictionary.put(new byte[] {'k','r','x','l'}, new Integer(8));
//        ArrayList list = new ArrayList();
//        list.add(new Integer(18));
//        list.add(dictionary);
//        
//        byte[] list_bytes = encode(list);
//        byte[] bencoded_string = new byte[] {'4',':',123,114,113,112};
//        byte[] bencoded_integer = new byte[] {'i','1','1','2','3','e'};
//        byte[] bencoded_list = new byte[] {'l','4',':','s','p','a','m','i','-','1','2','3','e','e'};
//        byte[] bencoded_dictionary = new byte[] {'d','4',':','s','p','a','m','i','-','1','2','3','e','e'};
//        
//        byte[] ben_dictionary = encode(dictionary);
//        ToolKit.print(ben_dictionary);
//
//        ToolKit.print(decode(ben_dictionary));
//        ToolKit.print(decode(bencoded_integer));
//        ToolKit.print(decode(bencoded_string));
//        ToolKit.print(decode(bencoded_list));
//        ToolKit.print(list);
//        ToolKit.print(decode(list_bytes));
//        
//        ToolKit.print(decode(bencoded_dictionary));
    }
    
    /*
     ********************************************
     ************ CONVENIENCE METHODS ***********
     ********************************************
     */
    public static final ByteBuffer getInfoBytes(byte[] torrent_file_bytes) throws BencodingException
    {
        Object[] vals = decodeDictionary(torrent_file_bytes,0);
        if(vals.length != 3 || vals[2] == null)
            throw new BencodingException("Exception: No info bytes found!");
        return (ByteBuffer)vals[2];
    }
    
    /*
     ********************************************
     ************ BDECODING METHODS *************
     ********************************************
     */
    
    public static final Object decode(byte[] bencoded_bytes) throws BencodingException
    {
        return decode(bencoded_bytes, 0)[1];
    }
    
    private static final Object[] decode(byte[] bencoded_bytes, int offset) throws BencodingException
    {
        switch(nextObject(bencoded_bytes, offset))
        {
        case DICTIONARY:
            return decodeDictionary(bencoded_bytes, offset);
        case LIST:
            return decodeList(bencoded_bytes, offset);
        case INTEGER:
            return decodeInteger(bencoded_bytes, offset);
        case STRING:
            return decodeString(bencoded_bytes, offset);
        default:
            return null;
        }
    }
    
    /**
     * 
     * @param bencoded_bytes the byte array of the bencoded integer.
     * @param offset the position of the 'i' indicating the start of the
     *        bencoded integer to be bdecoded.
     * @return an <code>Object[]</code> containing an <code>Integer</code> offset and the decoded
     *          <code>Integer</code>, in positions 0 and 1, respectively
     */
    private static final Object[] decodeInteger(byte[] bencoded_bytes, int offset) throws BencodingException
    {
        StringBuffer int_chars = new StringBuffer();
        offset++;
        for(; bencoded_bytes[offset] != (byte)'e' && bencoded_bytes.length > (offset); offset++)
        {
            if((bencoded_bytes[offset] < 48 || bencoded_bytes[offset] > 57) && bencoded_bytes[offset] != 45)
                throw new BencodingException("Expected an ASCII integer character, found " + (int)bencoded_bytes[offset]);
            int_chars.append((char)bencoded_bytes[offset]);
        }
        try 
        {
            offset++;   // Skip the 'e'
            return new Object[] {new Integer(offset),new Integer(Integer.parseInt(int_chars.toString()))};
        }
        catch(NumberFormatException nfe)
        {
            throw new BencodingException("Could not parse integer at position" + offset + ".\nInvalid character at position " + offset + ".");
        }
    }
    
    /**
     * 
     * @param bencoded_bytes
     * @param offset
     * @return an <code>Object[]</code> containing an <code>Integer</code> offset and the decoded
     *          <code>byte[]</code>, in positions 0 and 1, respectively
     */
    private static final Object[] decodeString(byte[] bencoded_bytes, int offset) throws BencodingException
    {
        StringBuffer digits = new StringBuffer();
        while(bencoded_bytes[offset] > '/' && bencoded_bytes[offset] < ':')
        {
            digits.append((char)bencoded_bytes[offset++]);
        }
        if(bencoded_bytes[offset] != ':')
        {
            throw new BencodingException("Error: Invalid character at position " + offset + ".\nExpecting ':' but found '" + (char)bencoded_bytes[offset] + "'.");
        }
        offset++;
        int length = Integer.parseInt(digits.toString());
        byte[] byte_string = new byte[length];
        System.arraycopy(bencoded_bytes, offset, byte_string, 0, byte_string.length);
        return new Object[] {new Integer(offset+length), ByteBuffer.wrap(byte_string)};
    }
    
    private static final Object[] decodeList(byte[] bencoded_bytes, int offset) throws BencodingException
    {
        ArrayList list = new ArrayList();
        offset++;
        Object[] vals;
        while(bencoded_bytes[offset] != (byte)'e')
        {
            vals = decode(bencoded_bytes,offset);
            offset = ((Integer)vals[0]).intValue();
            list.add(vals[1]);
        }
        offset++;
        return new Object[] {new Integer(offset), list};
    }
    
    /**
     * 
     * @param bencoded_bytes
     * @param offset
     * @return an <code>Object[]</code> containing an <code>Integer</code> offset and the decoded
     *          <code>HashMap</code>, in positions 0 and 1, respectively
     * @throws BencodingException
     */
    private static final Object[] decodeDictionary(byte[] bencoded_bytes, int offset) throws BencodingException
    {
        HashMap map = new HashMap();
        ++offset;
        ByteBuffer info_hash_bytes = null;
        while(bencoded_bytes[offset] != (byte)'e')
        {

            // Decode the key, which must be a byte string
            Object[] vals = decodeString(bencoded_bytes, offset);
            ByteBuffer key = (ByteBuffer)vals[1];
            offset = ((Integer)vals[0]).intValue();
            boolean match = true;
            for(int i = 0; i < key.array().length && i < 4; i++)
            {
                if(!key.equals(ByteBuffer.wrap(new byte[]{'i', 'n','f','o'})))
                {
                    match = false;
                    break;
                }
            }
            int info_offset = -1;
            if(match)
                info_offset = offset;
            vals = decode(bencoded_bytes, offset);
            offset = ((Integer)vals[0]).intValue();
            if(match)
            {
                info_hash_bytes = ByteBuffer.wrap(new byte[offset - info_offset]);
                info_hash_bytes.put(bencoded_bytes,info_offset, info_hash_bytes.array().length);
            }
            else if(vals[1] instanceof HashMap)
            {
                info_hash_bytes = (ByteBuffer)vals[2];
            }
            if(vals[1] != null)
                map.put(key,vals[1]);
        }

        return new Object[] {new Integer(++offset), map, info_hash_bytes};
    }
    
    private static final int nextObject(byte[] bencoded_bytes, int offset)
    {
        switch(bencoded_bytes[offset])
        {
        case (byte)'d':
            return DICTIONARY;
        case (byte)'i':
            return INTEGER;
        case (byte)'l':
            return LIST;
        case (byte)'0':
        case (byte)'1':
        case (byte)'2':
        case (byte)'3':
        case (byte)'4':
        case (byte)'5':
        case (byte)'6':
        case (byte)'7':
        case (byte)'8':
        case (byte)'9':
            return STRING;
        default:
            return INVALID;
        }
    }
    
    /*
     ********************************************
     ************ BENCODING METHODS *************
     ********************************************
     */
    public static final byte[] encode(Object o) throws BencodingException
    {
        if(o instanceof HashMap)
            return encodeDictionary((HashMap)o);
        else if(o instanceof ArrayList)
            return encodeList((ArrayList)o);
        else if(o instanceof Integer)
            return encodeInteger((Integer)o);
        else if(o instanceof ByteBuffer)
            return encodeString((ByteBuffer)o);
        else
            throw new BencodingException("Error: Object not of valid type for Bencoding.");
    }
    
    private static final byte[] encodeString(ByteBuffer string)
    {
        int length = string.array().length;
        int num_digits = 1;
        while((length /= 10) > 0)
        {
            num_digits++;
        }
        byte[] bencoded_string = new byte[length+num_digits+1];
        bencoded_string[num_digits] = (byte)':';
        System.arraycopy(string.array(), 0, bencoded_string, num_digits+1, length);
        for(int i = num_digits-1; i >= 0; i--)
        {
            bencoded_string[i] = (byte)((length % 10)+48);
            length /= 10;
        }
        return bencoded_string;
    }
    
    private static final byte[] encodeInteger(Integer integer)
    {
        int num_digits = 1;
        int int_val = integer.intValue();
        while((int_val /= 10) > 0)
            ++num_digits;
        int_val = integer.intValue();
        byte[] bencoded_integer = new byte[num_digits+2];
        bencoded_integer[0] = (byte)'i';
        bencoded_integer[bencoded_integer.length - 1] = (byte)'e';
        for(int i = num_digits; i > 0; i--)
        {
            bencoded_integer[i] = (byte)((int_val % 10)+48);
            int_val /= 10;
        }
        return bencoded_integer;
    }
    
    private static final byte[] encodeList(ArrayList list) throws BencodingException
    {
        byte[][] list_segments = new byte[list.size()][];
        for(int i = 0; i < list_segments.length;i++)
        {
            list_segments[i] = encode(list.get(i));
        }
        int total_length = 2;
        for(int i = 0 ; i < list_segments.length; i++)
            total_length += list_segments[i].length;
        byte[] bencoded_list = new byte[total_length];
        bencoded_list[0] = 'l';
        bencoded_list[bencoded_list.length-1] = 'e';
        int offset = 1;
        for(int i = 0; i < list_segments.length; i++)
        {
            System.arraycopy(list_segments[i],0,bencoded_list,offset,list_segments[i].length);
            offset += list_segments[i].length;
        }
        return bencoded_list;
    }
    
    private static final byte[] encodeDictionary(HashMap<ByteBuffer, Object> dictionary) throws BencodingException
    {
        TreeMap<ByteBuffer, Object> sorted_dictionary = new TreeMap<ByteBuffer, Object>();
        sorted_dictionary.putAll(dictionary);
        byte[][] dictionary_parts = new byte[sorted_dictionary.keySet().size()*2][];
        int k = 0;
        for(Iterator<ByteBuffer> i = sorted_dictionary.keySet().iterator(); i.hasNext();)
        {
            ByteBuffer key = i.next();
            dictionary_parts[k++] = encodeString(key);
            dictionary_parts[k++] = encode(sorted_dictionary.get(key));
        }
        
        int total_length = 2;
        for(int i = 0; i < dictionary_parts.length; i++)
        {
            total_length += dictionary_parts[i].length;
        }
        byte[] bencoded_dictionary = new byte[total_length];
        bencoded_dictionary[0] = 'd';
        bencoded_dictionary[bencoded_dictionary.length-1] = 'e';
        int offset = 1;
        for(int i = 0; i < dictionary_parts.length; i++)
        {
            System.arraycopy(dictionary_parts[i],0,bencoded_dictionary,offset,dictionary_parts[i].length);
            offset += dictionary_parts[i].length;
        }
        return bencoded_dictionary;
    }
}
