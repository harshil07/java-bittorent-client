import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.Calendar;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;
import java.nio.channels.*;

/**
 * The class that handles all peer messages
 * 
 * @author Tedd Noh
 * @author Harshil Shah
 */

public class PeerController extends Thread{
	
	private int piece_to_download;
	private int blocks_per_piece;
	private int total_blocks;
	private int blocks_for_last_piece;
	private int offset = 0;
	private int downloaded_so_far = 0;
	private Object lock;
	private PriorityQueue<Integer> piece_queue;
	private boolean keep_alive_sent = false;
	private final int block_length = RUBTClient.block_length;
	private Calendar start;
	private Calendar down_c;
	private Calendar up_c;
	
	/**
	 * The peer object assigned to this controller
	 */
	public Peer peer;
	
	private String id;
	
	private TorrentInfo torrent;
	
	private Logger logger;	
	
	private ByteBuffer buf = ByteBuffer.allocate(0);
	
	private Calendar c = Calendar.getInstance();
	
	private boolean am_alive=true;
	
	private RUBTClient rubt;
	private Controller controller;
	
	/**
	 * Returns the id for this controller
	 * @return
	 */
	public String getPCId()
	{
		return id;
	}
	
	PeerController (RUBTClient r, Controller c, String id,Peer p, Tracker t, TorrentInfo torrent_file)
	{	
		this.id = id;
		rubt = r;
		controller = c;
		peer = p;
		piece_to_download = -1;
		torrent = torrent_file;
		logger = rubt.logger;
		lock = new Object();
		piece_queue = new PriorityQueue<Integer>();
		blocks_per_piece = torrent.piece_length / block_length;
		total_blocks = (int) Math.ceil((double) torrent.file_length / block_length);
		blocks_for_last_piece = total_blocks - ((rubt.getNoPieces() - 1)*blocks_per_piece);
		start = Calendar.getInstance();
	}
	
	
	/**
	 * Downloaders run method
	 * <ul>
	 * <li>Performs handshake
	 * <li>Sends interested message
	 * <li>Waits for unchoke
	 * <li>Starts downloading the piece assigned to it by RUBTClient
	 * <li>File downloaded!
	 * </ul>
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		try
		{
			up_c = Calendar.getInstance();
			down_c = Calendar.getInstance();
			logger.debug("PeerController "+this.id+" is up and runnung.");
			//creating the reqd variables
			InputStream input_stream = peer.getPeerSocket().getInputStream();
		    DataInputStream in = new DataInputStream(new BufferedInputStream(input_stream));
			do
			{
				
				if(peer.getPeerSocket()==null || peer.getPeerSocket().isClosed())
					this.suicide();
				
				//request next piece if peer unchoked message has been received
				if(!peer.isPeerChoking() && piece_to_download>=0){
					peer.request(piece_to_download, offset * block_length, block_length);
				}
				else if(piece_to_download>=0){
					rubt.iAmDying(this, piece_to_download);
					piece_to_download = -1;
				}
				
				setpoint:
				while(am_alive){
					Thread.sleep(10); 
					//Thread.yield();
					if(peer.getPeerSocket()==null || peer.getPeerSocket().isClosed())
						this.suicide();
					
				synchronized(lock){
					//sends a have message for all pieces in the queue
				 while(!piece_queue.isEmpty())
				 {
					 int piece_no = piece_queue.remove().intValue();
					 peer.have(piece_no);
					 
				 }
				}
				//calls keep alive message
				sendKeepAlive();
				
				//if data in inputstream
				 if(in.available() > 0){ //TODO: check if it runs
					 
					keep_alive_sent = false;
					
					int len = peer.getPeerResponseInt();
					if (len != 0)
					{
						c = Calendar.getInstance();
						
						byte id = peer.getPeerResponseByte(); 
						
						//if message received is not bitfield msg then sets the flag to false
						if(id!=Peer.KEY_BITFIELD)
							peer.receive_bitfield = false;
						
						switch (id)
						{	
						case Peer.KEY_CHOKE:
							chokeReceived();
							break;
							
						case Peer.KEY_UNCHOKE:
							receiveUnchoke();
							break setpoint;
							
						case Peer.KEY_INTERESTED:
							//if (peer.unchoke() && !peer.isPeerInterested())
							if (peer.isClientChoking() && peer.unchoke())
							{
								peer.setPeerInterested(true);
								peer.setClientChoking(false);
								logger.debug("Peer "+peer.getPeerID()+" sent an interested message");
							}
							else
							{
								logger.error("Peer "+peer.getPeerID()+" sent illegal interested message. Violated Protocol so closing connection.");
								this.suicide();
							}
							break;
							
						case Peer.KEY_UNINTERESTED:
							peer.setPeerInterested(false);
							break;
							
						case Peer.KEY_HAVE:
							receiveHave();
							break setpoint;
							
						case Peer.KEY_BITFIELD:
							//if bitfield flag is set
							if(peer.receive_bitfield){
								receiveBitField(len);
								peer.interested();
							}
							else{
							//else bitfield was received out of order and hence close connection
								logger.debug("Received bitfield out of sync... closing connection");
								this.suicide();
							}
							break;
							
						case Peer.KEY_REQUEST:
							if(peer.isClientChoking())
							{
								logger.error("Peer "+peer.getPeerID()+" sent a request message while choked. Violated Protocol and so closing connection.");
								this.suicide();
								break;
							}
							
							int index = peer.getPeerResponseInt();
							int offset = peer.getPeerResponseInt();
							int length = peer.getPeerResponseInt();
							logger.debug("Request received from peer "+peer.getPeerID()+" for i="+index+" o="+offset);
							sendPiece(index,offset,length);
							break;
							
						case Peer.KEY_PIECE:
							if(downloadPiece(len))
							{
								break setpoint;
							}
							break;
							
						case Peer.KEY_CANCEL:
							//Nothing can be done here since requests are responded to immediately and not queued.
							break;
							
						case Peer.KEY_PORT:	//This is not needed
							break;
						default:
							break;
						}
					}
					else{
						//we were sent the keep alive message
						sendKeepAlive();
					}
				
					try
					{
						Thread.sleep(0);
					} catch (InterruptedException ie)
					{
						logger.debug("CANNOT INVOKE SLEEP METHOD!");
					}
				 }
				}
			}while (((piece_to_download = rubt.getNextPieceToDownload(this)) >= 0 && am_alive)||am_alive);
			//closing the peer connections
			peer.close();
			logger.info("PeerController: "+this.id+" is done downloading");
			
		} catch (IOException ioe)
		{
			rubt.iAmDying(this,piece_to_download);
			peer.close();
			logger.error("COULD NOT GET INPUT STREAM FOR PEER SOCKET!");
		} catch(Exception e)
		{
			rubt.iAmDying(this,piece_to_download);
			peer.close();
			logger.error(e.getMessage());
		}
		
	}
	
	private void sendPiece(int index, int offset, int length)
	{
		try{
			
			//peer already has the piece but still requesting it
		if(peer.bit_set.get(index))
		{
			logger.error("Peer "+peer.getPeerID()+" violated protocol. Requested piece that it already has");
			this.suicide();
			return;
		}
			//if we have the piece
		if (rubt.getCompletedBitSet().get(index))
		{
			FileInputStream fis = new FileInputStream(rubt.getOutputFileName());
			FileChannel input_channel = fis.getChannel();
			
			ByteBuffer block_buffer = ByteBuffer.wrap(new byte[length]);
			input_channel.read(block_buffer, index * block_length);
			//send the piece
			peer.piece(index, offset, block_buffer.array());
			logger.debug("Uploaded bytes to peer "+peer.getPeerID()+" for i="+index+" o="+offset);
			onUpload(block_buffer.array().length);
		}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			logger.error(e.toString());
			logger.error("Could not send i="+index+" o="+offset+" to peer "+peer.getPeerID());
		}
	}
	
	private synchronized void onUpload(int length)
	{
		rubt.uploadBytes(length);
		Calendar tmp = Calendar.getInstance();
		double time = (tmp.getTimeInMillis()/1000) - (up_c.getTimeInMillis()/1000);
		peer.down_rate  = length/1000*time;
		up_c = tmp;
		peer.downloadedBytes(length);
		peer.avg_down_rate = peer.getDownloadedBytes()/(1000*((tmp.getTimeInMillis()/1000) - (start.getTimeInMillis()/1000)));
		controller.onUpload();
		if(rubt.gui!=null)
		rubt.gui.update(new ActionEvent(this,GUI.BYTES_UPLOADED,null));
	}
	
	private synchronized void onDownload(int length)
	{
		rubt.downloadBytes(length);
		Calendar tmp = Calendar.getInstance();
		double time = (tmp.getTimeInMillis()/1000) - (down_c.getTimeInMillis()/1000);
		peer.up_rate = length/1000*time;
		down_c = tmp;
		peer.uploadedBytes(length);
		peer.avg_up_rate = peer.getUploadedBytes()/(1000*((tmp.getTimeInMillis()/1000) - (start.getTimeInMillis()/1000)));
		controller.onDownload();
		if(rubt.gui!=null)
		rubt.gui.update(new ActionEvent(this,GUI.BYTES_DOWNLOADED,null));
	}
	
	private void receiveHave()
	{
		int piece_index = peer.getPeerResponseInt();
		logger.debug("Have message for piece "+piece_index+" received from peer "+peer.getPeerID());
		
		if(piece_index<0 || piece_index>=rubt.getNoPieces())
		{
			logger.debug("Invalid have message received hence closing connection.");
			this.suicide();
		}
		
		
		//if peer has piece that not in global bit set and we are not interested then
		//send interested message
		if(!rubt.getGlobalBitSet().get(piece_index)&&!peer.isClientInterested())
		{
			peer.interested();
		}
		
		if(peer.bit_set.get(piece_index))
		{
			logger.error("Peer "+peer.getPeerID()+" sent a have message for piece it already had before\n Violated protocol so closing connection");
			this.suicide();
		}
		
		peer.bit_set.set(piece_index,true);
		rubt.updateBit(piece_index);
	}
	
	private void chokeReceived()
	{
		logger.debug("Peer "+peer.getPeerID()+" is choking controller "+this.id);
		//we have been choked give away the piece currently downloading throw away old data
		rubt.iAmDying(this, piece_to_download);
		peer.setPeerChoking(true);
		piece_to_download = -1;
		downloaded_so_far = 0;
		offset = 0;
		buf = ByteBuffer.allocate(0);
		
	}
	
	private byte[] digest(byte[] bytes)
	{
		MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA-1");
			sha.update(bytes);
			return sha.digest();
		}
		catch(Exception e)
		{}
		return null;
	}
	
	private boolean verify(int index,byte[] received_bytes)
	{
			byte[] received_hash = digest(received_bytes);
			byte[] hash = torrent.piece_hashes[index].array();
			if(MessageDigest.isEqual(hash, received_hash))
			{
				logger.debug("SHA-1 hash verified for piece:"+index);
				return true;
			}
			else{
				logger.error("Could not verify has for piece "+index+"...Who ate the bytes??");
				peer.corrupted_bit_set.set(piece_to_download, true);
				rubt.iAmDying(this, piece_to_download);
			}
		return false;
	}
		 
	private boolean isLastPiece()
	{
		return piece_to_download == rubt.getNoPieces()-1;
	}
	
	/**
	 * Checks if this Controllers Peer has piece i
	 * @param i piece index
	 * @return
	 */
	public boolean pieceExists(int i)
	{
		return peer.hasPiece(i);
	}
	
	private void receiveBitField(int length) throws Exception
	{
		int l = 0;
		BitSet bs = new BitSet(rubt.getNoPieces());
		byte bitfield_array[] = peer.getPeerResponse(length-1);
		
		byte bit_mask = (byte)0x80;
		//reading in bitfield bit by bit
		for(int k=0;k<bitfield_array.length;k++)
		{
			byte bitfield = bitfield_array[k];
			
			for(int i=0;i<8;i++){
				if(l<rubt.getNoPieces())	
				{
					bs.set(k*8+i, ((bitfield & bit_mask) == bit_mask) ? true : false); 
					bitfield = (byte)(bitfield >>> 2);
					l++;
				}
			}
		}
	
		if(l == rubt.getNoPieces())
		{
			//update the global bitset
			logger.debug("BitField successfully received");
			peer.bit_set = bs;
			rubt.uploadBitSet(bs);
			logger.info("Bitfield received - peerController "+this.id);
		}
		else{
			throw new Exception("BitField Error: Size does not match");
		}
	}
	
	private void receiveUnchoke()
	{
		logger.info("Unchoke received - peerController "+this.id);
		peer.setPeerChoking(false);
		//peer.request((piece_to_download=rubt.getNextPieceToDownload(this)), offset * block_length, block_length);
	}
	

	
	private boolean downloadPiece(int len)
	{
		piece_to_download = peer.getPeerResponseInt();
		offset = peer.getPeerResponseInt() / block_length;
		logger.debug("i = " + piece_to_download + ", o = " + offset);
		
		
		byte[] received_bytes = peer.getPeerResponse(len - 9);
		onDownload(received_bytes.length);
		byte[] tmp_buffer = buf.array();
		
		//allocate only the amount of bytes required
		
		//if last piece and last block
		if(downloaded_so_far+1==blocks_per_piece && isLastPiece())
			buf = ByteBuffer.allocate(buf.capacity()+ (torrent.file_length % block_length));
		else //else
			buf = ByteBuffer.allocate(buf.capacity()+block_length);
		
		//put the old bytes + the new received_bytes in the buffer
		buf.put(tmp_buffer);
		buf.put(received_bytes);
		
		//if piece complete or if last block of last piece then verify, send have msg & write
		if(downloaded_so_far+1==blocks_per_piece || (downloaded_so_far+1==blocks_for_last_piece && isLastPiece()))
		{	
			if(verify(piece_to_download,buf.array())){
				rubt.writeBytes(buf,piece_to_download);
				rubt.leftBytes(buf.array().length);
				rubt.pieceDownloaded(piece_to_download);
				controller.sendHave(piece_to_download,this);
			}
			buf = ByteBuffer.allocate(0);
		}
		
		received_bytes = null;
		downloaded_so_far++;
		

		
		//if not last block
		if (downloaded_so_far + 1 < blocks_per_piece)
		{
				//requesting next block of data for the same piece
				peer.request(piece_to_download, ++offset * block_length, block_length);
		}
		//if last block
		else if (downloaded_so_far + 1 == blocks_per_piece)
		{
			//This is the last block that needs to be downloaded
			//It may have an irregular length (not 16384 bytes)
			
			int length;
			
			if(isLastPiece())
				length = torrent.file_length % block_length;
			else
				length = block_length;
			
			peer.request(piece_to_download, ++offset * block_length, length);
			
		} else
		{
			//piece downloaded. break out and get next piece
			downloaded_so_far = 0;
			offset = 0;
			buf = ByteBuffer.allocate(0);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Safely closes this PeerController
	 */
	public void suicide()
	{
		logger.debug("PeerController "+getPCId()+" is committing suicide");
		rubt.iAmDying(this, piece_to_download);
		peer.close();
		am_alive = false;
	}
	
	private void sendKeepAlive()
	{
		//if the time elapsed since last message sent was more than 2min then send a keep alive message
		Calendar c2 = Calendar.getInstance();
		if(c2.getTimeInMillis() - c.getTimeInMillis() > 120000)
		{
			//if keep alive already sent once and no response for 2min close connection
			if(keep_alive_sent)
			{
				this.suicide();
				return;
			}
			keep_alive_sent = true;
			peer.keepalive();
		}	
	}
	
	/**
	 * Adds the piece to the peers waiting queue to be sent a have message
	 * @param piece_index
	 */
	public void addToQueue(int piece_index)
	{
		synchronized(lock){
			piece_queue.add(new Integer(piece_index));
		}
	}

}
