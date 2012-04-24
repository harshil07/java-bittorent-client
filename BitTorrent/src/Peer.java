import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.BitSet;
/**
 * Peer object.
 * Models a actual peer and all functions needed to communicate with it
 * 
 * @author Harshil Shah
 * @author Tedd Noh
 */

public class Peer implements Comparable<Peer>{

	private String 					client_id="";
	private String 					peer_id="";
	private byte[] 					info_hash;
	private String 					_ip="";
	private int 					_port;
	private Socket 					sock;
	private DataOutputStream 		client_to_peer;
	private DataInputStream 		peer_to_client;
	private boolean 				am_choking;
	private boolean 				am_interested;
	private boolean 				peer_choking;
	private boolean 				peer_interested;
	/**
	 * Flag for if the bitfield has been received
	 */
	public boolean 					receive_bitfield;
	private boolean 				handshake_performed;
	private boolean 				handshake_confirmed;
	
	/**
	 * Key for choke message
	 */
	final static int KEY_CHOKE = 		0;
	/**
	 * Key for unchoke message
	 */
	final static int KEY_UNCHOKE = 		1;
	/**
	 * Key for interested message
	 */
	final static int KEY_INTERESTED = 	2;
	/**
	 * Key for uninterested message
	 */
	final static int KEY_UNINTERESTED = 3;
	/**
	 * Key for have message
	 */
	final static int KEY_HAVE = 		4;
	/**
	 * Key for bitfield message
	 */
	final static int KEY_BITFIELD = 	5;
	/**
	 * Key for request message
	 */
	final static int KEY_REQUEST = 		6;
	/**
	 * Key for piece message
	 */
	final static int KEY_PIECE = 		7;
	/**
	 * Key for cancel message
	 */
	final static int KEY_CANCEL = 		8;
	/**
	 * Key for port message
	 */
	final static int KEY_PORT = 		9;
	
	private Logger logger;
	
	final static byte[] interested = 		{0,0,0,1,2};
	final static byte[] uninterested = 		{0,0,0,1,3};
	final static byte[] choke = 			{0,0,0,1,0};
	final static byte[] unchoke = 			{0,0,0,1,1};
	final static byte[] empty_bitfield = 	{0,0,0,2,5,0};
	final static byte[] keep_alive = 		{0,0,0,0};
	//final static byte[] have = 			{0,0,0,5,4};
	//final static byte[] request = 		{0,0,1,3,6}; 
	//final static byte[] piece = 			{0,0,0,9,7};

	private double bytes_downloaded	= 0;
	private double bytes_uploaded 	= 0;
	public double up_rate			= 0;
	public double down_rate			= 0;
	public double avg_up_rate		= 0;
	public double avg_down_rate		= 0;

	BitSet bit_set;
	//corrupted bit set represents pieces that the client tried to download but did pass verification
	BitSet corrupted_bit_set;
	
	public Peer(Logger log,int no_pieces, String cid, String pid, String ip, int port, byte[] infohash)
	{
			logger = log;
			client_id = cid;
			peer_id = pid;
			_ip = ip;
			_port = port;
			info_hash = infohash;
			bit_set = new BitSet(no_pieces);
			corrupted_bit_set = new BitSet(no_pieces);
			sock = null;
			client_to_peer = null;
			peer_to_client = null;
			handshake_performed = false;
			handshake_confirmed = false;
			
			//All connections to peers start out as choked and not interested
			am_choking = true;
			am_interested = false;
			peer_choking = true;
			peer_interested = false;
			receive_bitfield = false;
	}
	
	
	/*
	 **************************************
	 ********** Peer Get Methods **********
	 **************************************
	 */
	
	/**
	 * Returns the peer id
	 * @return String
	 */
	public String getPeerID()
	{
		return peer_id;
	}
	
	/**
	 * Returns the peer ip
	 * @return String
	 */
	public String getPeerIP()
	{
		return _ip;
	}
	
	/**
	 * Returns the peer port
	 * @return int
	 */
	public int getPeerPort()
	{
		return _port;
	}
	/**
	 * Returns the peer socket
	 * @return Socket
	 */
	public Socket getPeerSocket()
	{
		return sock;
	}
	
	/**
	 * Sets the peer socket and also initializes the i/o streams
	 * @param s
	 */
	public void setPeerSocket(Socket s)
	{
		try
		{
			sock = s;
			client_to_peer = new DataOutputStream(sock.getOutputStream());
			peer_to_client = new DataInputStream(sock.getInputStream());
			
		} catch (IOException ioe)
		{
			logger.error(ioe.getMessage());
			
		}
	}
	
	/**
	 * Checks if we are choking this peer
	 * @return
	 */
	public boolean isClientChoking()
	{
		return am_choking;
	}
	
	/**
	 * Sets this peer's choking status
	 * @param choke
	 */
	public synchronized void setClientChoking(boolean choke)
	{
		am_choking = choke;
	}
	
	/**
	 * Checks if we are interested in this peer
	 * @return
	 */
	public boolean isClientInterested()
	{
		return am_interested;
	}
	
	/**
	 * Sets this peer's interested status
	 * @param interested
	 */
	public synchronized void setClientInterested(boolean interested)
	{
		am_interested = interested;
	}
	
	/**
	 * Checks if this peer is choking us
	 * @return
	 */
	public boolean isPeerChoking()
	{
		return peer_choking;
	}
	
	/**
	 * Sets the clients choking status
	 * @param choke
	 */
	public synchronized void setPeerChoking(boolean choke)
	{
		peer_choking = choke;
	}
	
	/**
	 * Checks if this peer is interested in us.
	 * @return
	 */
	public synchronized boolean isPeerInterested()
	{
		return peer_interested;
	}
	
	/**
	 * Sets the clients interested status
	 * @param interested
	 */
	public void setPeerInterested(boolean interested)
	{
		peer_interested = interested;
	}
	
	/**
	 * Returns the total bytes upload by this peer
	 * @return
	 */
	public double getUploadedBytes()
	{
		return bytes_uploaded;
	}
	
	/**
	 * Returns the total bytes downloaded by this peer
	 * @return
	 */
	public double getDownloadedBytes()
	{
		return bytes_downloaded;
	}
	
	
	
	/*
	 *************************************************
	 *********** Peer Communication Methods **********
	 *************************************************
	 */
	
	
	/**
	 * Performs the peer handshake
	 * Retrieves the peers response
	 * and checks to see if the info hashes match
	 * 
	 * @return boolean
	 */
	public boolean handshake()
	{
		logger.debug("Performing Handshake with peer: "+this.peer_id);
		if (handshake_performed || sock != null)
		{
			logger.debug("HANDSHAKE ALREADY PERFORMED FOR THIS PEER! ADDITIONAL HANDSHAKE NOT PERFORMED.");
			return true;		//Does not perform a handshake if it has already been performed successfully
		}
		
		try
		{
			String pstr = "BitTorrent protocol";		//String identifier of the current BitTorrent protocol
			byte[] msg = new byte[49 + pstr.length()];
			//byte[] response = new byte[msg.length];
			
			int i = 0;
			int offset = 0;
			msg[i] = ((byte) pstr.length());
			//Sets up the part of the byte array corresponding to the BitTorrent protocol
			i = 1;
			offset = i;
			for (i=i; i<pstr.length()+1; i++)
				msg[i] = (byte) pstr.charAt(i - offset);
			
			//Sets up the 8 reserved bytes in the array
			for (i=i; i<pstr.length() + 9; i++)
				msg[i] = (byte) 0;
			
			//Sets up the 20 byte info_hash in the array
			offset = i;
			for (i=i; i<pstr.length() + 29; i++)
				msg[i] = info_hash[i - offset];
			
			//Sets up the 20 byte peer id in the array
			offset = i;
			for (i=i; i<pstr.length() + 49; i++)
				msg[i] = (byte) client_id.charAt(i - offset);
			
			sock = new Socket(_ip, _port);
			
			//peer_to_client = new DataInputStream(sock.getInputStream());
			client_to_peer = new DataOutputStream(sock.getOutputStream());
			
			client_to_peer.write(msg, 0, msg.length);
			client_to_peer.flush();
			//peer_to_client.readFully(response);
			
			logger.debug("Client Messag: " + new String(msg));
			//logger.debug("Peer Response: " + new String(response));
			//logger.debug("response length: " + response.length);
			
			//sets the flag for receiving bitfield
			receive_bitfield = true;
			handshake_performed = true;
			return true;
		} catch(Exception ioe)
		{
			logger.error(ioe.getMessage());
			close();
			return false;
		}
	}
	
	public boolean confirmHandshake()
	{
		if(handshake_confirmed)
			return true;
			
		try
		{
			String pstr = "BitTorrent protocol";
			byte[] response = new byte[49 + pstr.length()];
			
			peer_to_client = new DataInputStream(sock.getInputStream());
			
			peer_to_client.readFully(response);
			
			logger.debug("Peer Response: " + new String(response));
			logger.debug("response length: " + response.length);
			
			//Checks if connection should be dropped
			//Checks if Peer IDs match
			for (int x=1; x<=20; x++)
			{
				if (response[response.length - x] != (byte) peer_id.charAt(peer_id.length() - x))
				{
					logger.debug("MISMATCHING PEER_ID! SOCKET CONNECTION HAS BEEN CLOSED.");
					sock.close();
					return false;
					//break;
				}
			}
			
			//Checks if info_hashs match
			for (int y=1; y<=20; y++)
			{
				if (response[response.length - 20 - y] != info_hash[info_hash.length - y])
				{
					logger.debug("MISMATCHING INFO_HASH! SOCKET CONNECTION HAS BEEN CLOSED.");
					sock.close();
					return false;
				}
			}
			
			logger.debug("Handshake successfull with peer "+peer_id+"... Now we rolling");
			handshake_confirmed = true;
			return true;
		} catch (IOException ioe)
		{
			logger.error(ioe.getMessage());
			close();
			return false;
		}
	}
	
	/**
	 * Sends this peer the keep alive message
	 * @return true
	 */
	public synchronized boolean keepalive()
	{
		try
		{
			client_to_peer.write(keep_alive);
			client_to_peer.flush();
			logger.debug("KeepAlive message sent to peer "+peer_id);
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND KEEP ALIVE MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Sends this peer the choke message
	 * @return
	 */
	public synchronized boolean choke()
	{
		try
		{
			client_to_peer.write(choke);
			client_to_peer.flush();
			logger.debug("Choke message sent to peer "+this.getPeerID());
			
			am_choking = true;
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND CHOKE MESSAGE TO PEER!");
			return false;
		} catch(Exception e)
		{
			logger.error(e.getMessage());
			logger.error("COULD NOT SEND CHOKE MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Sends this peer the unchoke message
	 */
	public synchronized boolean unchoke()
	{
		try
		{
			client_to_peer.write(unchoke);
			client_to_peer.flush();
			logger.debug("Unchoke message sent to peer "+this.getPeerID());
			
			am_choking = false;
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND UNCHOKE MESSAGE TO PEER!");
			return false;
		}
		catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND UNCHOKE MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Sends this peer an interested message
	 * @return boolean
	 */
	public synchronized boolean interested()
	{
		try
		{
			client_to_peer.write(interested, 0, interested.length);
			client_to_peer.flush();
			logger.debug("Interested message sent");
			
			am_interested = true;
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND INTERESTED MESSAGE TO PEER!");
			return false;
		} catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND INTERESTED MESSAGE TO PEER!");
			return false;
		}
		
	}
	
	/**
	 * Sends this peer the uninterested message
	 * @return boolean
	 */
	public synchronized boolean uninterested()
	{
		try
		{
			client_to_peer.write(uninterested);
			client_to_peer.flush();
			logger.debug("Uninterested message sent");
			
			am_interested = false;
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND UNINTERESTED MESSAGE TO PEER!");
			return false;
		}
		catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND UNINTERESTED MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Sends this peer the have message
	 * @param  piece_index
	 * @return boolean
	 */
	public synchronized boolean have(int piece_index)
	{
		try
		{
			if(!sock.isClosed()){
				ByteBuffer have_buffer = ByteBuffer.allocate(9);
				have_buffer.put(new byte[] {0,0,0,5,4});
				have_buffer.putInt(piece_index);
				client_to_peer.write(have_buffer.array());
				client_to_peer.flush();
				logger.debug("Have message for piece "+piece_index+" sent to peer "+peer_id);
				return true;
			}
			else
			{
				logger.error("Socket closed at peer: "+peer_id);
				return false;
			}
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.toString());
			logger.error("COULD NOT SEND HAVE MESSAGE TO PEER "+peer_id);
			return false;
		}catch (Exception ioe)
		{
			logger.error(ioe.toString());
			logger.error("COULD NOT SEND HAVE MESSAGE TO PEER "+peer_id);
			return false;
		}
		
	}
	
	/**
	 * Sends this peer the bitfield message. Takes a byte array as input.
	 * @param bitfield
	 * @return
	 */
	public synchronized boolean bitfield(byte[] bitfield)
	{
		try
		{
			ByteBuffer bitfield_buffer = ByteBuffer.allocate(5 + bitfield.length);
			bitfield_buffer.putInt(1 + bitfield.length);
			bitfield_buffer.put((byte) 5);
			bitfield_buffer.put(bitfield);
			client_to_peer.write(bitfield_buffer.array());
			client_to_peer.flush();
			logger.debug("Bitfield message sent");
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND BITFIELD MESSAGE");
			return false;
		}catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND BITFIELD MESSAGE");
			return false;
		}
	}
	
	/**
	 * Sends this peer the bitfield message. Takes a BitSet as input.
	 * @param bitfield
	 * @return
	 */
	public synchronized boolean bitfield(BitSet bitfield)
	{
		try
		{
			byte[] bitfield_bytes = new byte[(bitfield.length()-1)/8+1];
			
			//Initialize to zero
			for (int x=0; x<bitfield_bytes.length; x++)
				bitfield_bytes[x] = (byte) 0;
			
			//Converts Bitset to byte array
			for (int i=0; i<bitfield.length(); i++) {
				if (bitfield.get(i)) {
			            	bitfield_bytes[i/8] |= 1<<(7-(i%8));
			        }
			}
	        
	        ByteBuffer bitfield_buffer = ByteBuffer.allocate(5 + bitfield_bytes.length);
			bitfield_buffer.putInt(1 + bitfield_bytes.length);
			bitfield_buffer.put((byte) 5);
			bitfield_buffer.put(bitfield_bytes);
			client_to_peer.write(bitfield_buffer.array());
			client_to_peer.flush();
			logger.debug("Bitfield message sent");
			return true;
		} catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND BITFIELD MESSAGE");
			return false;
		}
	}
	
	/**
	 * Sends this peer the request message.
	 * Requesting a piece of data
	 * 
	 * @param index
	 * @param begin
	 * @param length
	 * @return boolean
	 */
	public synchronized boolean request(int index, int begin, int length)
	{
		try
		{
			ByteBuffer request_buffer = ByteBuffer.allocate(17);
			request_buffer.put(new byte[] {0,0,0,13,6});
			request_buffer.putInt(index);
			request_buffer.putInt(begin);
			request_buffer.putInt(length);
			client_to_peer.write(request_buffer.array());
			client_to_peer.flush();
			logger.debug("Request message sent");
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND REQUEST MESSAGE TO PEER!");
			return false;
		}catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND REQUEST MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Sends this peer the piece message
	 * @param index piece index
	 * @param begin
	 * @param block
	 * @return boolean
	 */
	public synchronized boolean piece(int index, int begin, byte[] block)
	{
		try
		{
			ByteBuffer piece_buffer = ByteBuffer.allocate(13 + block.length);
			piece_buffer.putInt(9 + block.length);
			piece_buffer.put((byte) 7);
			piece_buffer.putInt(index);
			piece_buffer.putInt(begin);
			piece_buffer.put(block);
			client_to_peer.write(piece_buffer.array());
			client_to_peer.flush();
			logger.debug("Piece message sent");
			return true;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND PIECE MESSAGE TO PEER!");
			return false;
		}catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT SEND PIECE MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Sends this peer the cancel message
	 * @param index
	 * @param begin
	 * @param length
	 * @return
	 */
	public synchronized boolean cancel(int index, int begin, int length)
	{
		try
		{
			ByteBuffer cancel_buffer = ByteBuffer.allocate(17);
			cancel_buffer.put(new byte[] {0,0,0,13,8});
			cancel_buffer.putInt(index);
			cancel_buffer.putInt(begin);
			cancel_buffer.putInt(length);
			client_to_peer.write(cancel_buffer.array());
			client_to_peer.flush();
			logger.debug("Cancel message sent");
			return true;
		} catch (IOException ioe)
		{
			logger.error("COULD NOT SEND CANCEL MESSAGE TO PEER!");
			return false;
		}
	}
	
	/**
	 * Used to get this peers complete response
	 * @param length
	 * @return byte[]
	 */
	public synchronized byte[] getPeerResponse(int length)
	{
		try
		{
			byte[] response = new byte[length];
			peer_to_client.readFully(response);
			logger.debug("Peer Response: ");
			/*for (int i=0; i<length; i++)
			{
				logger.debug(" | " + response[i] + " | ");
			}*/			
			
			return response;
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT GET PEER RESPONSE!");
			return null;
		}catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT GET PEER RESPONSE!");
			return null;
		}
	}
	
	/**
	 * Used to get this peers response.
	 * @return byte
	 */
	public synchronized byte getPeerResponseByte()
	{
		try
		{
			return peer_to_client.readByte();
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT GET PEER RESPONSE! -1 returned");
			return -1;
		}
		catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT GET PEER RESPONSE! -1 returned");
			return -1;
		}
	}
	
	/**
	 * Gets this peers response
	 * @return int
	 */
	public synchronized int getPeerResponseInt()
	{
		try
		{
			return peer_to_client.readInt();
		} catch (IOException ioe)
		{
			close();
			logger.error(ioe.getMessage());
			logger.error("COULD NOT GET PEER RESPONSE! -1 returned");
			return -1;
		}catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT GET PEER RESPONSE! -1 returned");
			return -1;
		}
	}
	
	/**
	 * Performs the final clean up of closing sockets
	 * and i/o streams
	 */
	public void close()
	{
		try
		{
			if(!sock.isClosed())
			{	
				sock.close();
				client_to_peer.close();
				peer_to_client.close();
			}
		} catch (Exception ioe)
		{
			logger.error(ioe.getMessage());
			logger.error("COULD NOT CLOSE SOCKET!");
		}
	}
	
	public void downloadedBytes(double i)
	{
		bytes_downloaded += i;
	}
	
	public void uploadedBytes(double i)
	{
		bytes_uploaded += i;
	}
	
	/**
	 * Checks to see if the piece 'i'
	 * exists in its this peers bitset
	 * 
	 * @return boolean
	 */
	public synchronized boolean hasPiece(int i)
	{
		return bit_set.get(i);
	}


	@Override
	public int compareTo(Peer arg0) {
		// TODO Auto-generated method stub
		return 0;
	}
	
}
