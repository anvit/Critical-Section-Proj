import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import com.sun.nio.sctp.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

class ListType
{
	private long timestamp;
	private int id;
	public long getTimestamp()
	{
		return timestamp;
	}
	public void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}
	public int getId()
	{
		return id;
	}
	public void setId(int id)
	{
		this.id = id;
	}
}


public class ProjCS extends Thread
{
	public static final int MESSAGE_SIZE = 100;
	static int nodes = 0;
	static ProjCS obj = null;
	static ArrayList<String> hosts = new ArrayList<String>();
	static ArrayList<String> ports = new ArrayList<String>();
	static ArrayList<String> paths = new ArrayList<String>();
	static ArrayList<ListType> request_queue = new ArrayList<ListType>();
	static ArrayList<Integer> grant_set = new ArrayList<Integer>();
	static ArrayList<Integer> received_grant_set = new ArrayList<Integer>();
	static ArrayList<Integer> failed_set= new ArrayList<Integer>();
	static int current_grant;
	static String[] own_quorums;
	static int proc_number;

	/*

	Message types:
	REQUEST:	1
	GRANT:		2
	INQUIRE:	3
	RELEASE:	4
	YEILD:		5
	FAILED:		6
	
	EMERGENCY TERMINATION: 0 //Unused!!

	*/



	//               ,,    ,,                           
	//   .g8"""bgd `7MM    db                     mm    
	// .dP'     `M   MM                           MM    
	// dM'       `   MM  `7MM  .gP"Ya `7MMpMMMb.mmMMmm  
	// MM            MM    MM ,M'   Yb  MM    MM  MM    
	// MM.           MM    MM 8M""""""  MM    MM  MM    
	// `Mb.     ,'   MM    MM YM.    ,  MM    MM  MM    
	//   `"bmmmd'  .JMML..JMML.`Mbmmd'.JMML  JMML.`Mbmo

	public void client(int port,String host,String message)
	{
		ByteBuffer byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
		boolean scanning=true;
		SocketAddress socketAddress = null;
		SctpChannel sctpChannel = null;
		Random randomGenerator = new Random();
		while(scanning)
		{
			try
			{
				socketAddress = new InetSocketAddress(host,port);
				sctpChannel = SctpChannel.open();
				sctpChannel.bind(new InetSocketAddress(randomGenerator.nextInt(10000)));
				sctpChannel.connect(socketAddress);
				scanning = false;
			}
			catch(ConnectException e)	//Possible existing connection
			{
				try
				{
					Thread.sleep(2000);//2 seconds
				}
				catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
			catch(SocketException e)	//For checking if port is being used by existing services
			{
				try
				{
					System.out.println("Port in use, retrying...");
					Thread.sleep(2000);//2 seconds
				}
				catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
			catch(UnknownHostException ex)	//Server isn't up yet
			{
				try
				{
					Thread.sleep(2000);//2 seconds
				}
				catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
			catch(IOException ex)
			{
				ex.printStackTrace();
			}
		}

		try
		{			
			MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);
			byteBuffer.put(message.getBytes());
			byteBuffer.flip();
			sctpChannel.send(byteBuffer,messageInfo);
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}


	//  .M"""bgd                                             
	// ,MI    "Y                                             
	// `MMb.      .gP"Ya `7Mb,od8 `7M'   `MF'.gP"Ya `7Mb,od8 
	//   `YMMNq. ,M'   Yb  MM' "'   VA   ,V ,M'   Yb  MM' "' 
	// .     `MM 8M""""""  MM        VA ,V  8M""""""  MM     
	// Mb     dM YM.    ,  MM         VVV   YM.    ,  MM     
	// P"Ybmmd"   `Mbmmd'.JMML.        W     `Mbmmd'.JMML.   
                                                      
	public void srvr(int port)
	{
		try
		{
			SctpServerChannel sctpServerChannel = SctpServerChannel.open();
			InetSocketAddress serverAddr = new InetSocketAddress(port);
			sctpServerChannel.bind(serverAddr);
			System.out.println("Server started at - " + port);
			while(true)
			{
				ByteBuffer byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
				SctpChannel sctpChannel = sctpServerChannel.accept();
				MessageInfo messageInfo = sctpChannel.receive(byteBuffer,null,null);
				String message = byteToString(byteBuffer);
				// System.out.println(message);
				String[] message_sections = message.split("-");
				int sender = Integer.parseInt(message_sections[0]);
				int typ = Integer.parseInt(message_sections[1]);
				long ts = Long.parseLong(message_sections[2].trim());
				int inq = -1;
				if(message_sections.length==4)
				{
					inq = Integer.parseInt(message_sections[3].trim());
				}
				server_application(sender,typ,ts,inq);
			}
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}


//  .M"""bgd                                                       db                          
// ,MI    "Y                                                      ;MM:                         
// `MMb.      .gP"Ya `7Mb,od8 `7M'   `MF'.gP"Ya `7Mb,od8         ,V^MM.   `7MMpdMAo.`7MMpdMAo. 
//   `YMMNq. ,M'   Yb  MM' "'   VA   ,V ,M'   Yb  MM' "'        ,M  `MM     MM   `Wb  MM   `Wb 
// .     `MM 8M""""""  MM        VA ,V  8M""""""  MM            AbmmmqMA    MM    M8  MM    M8 
// Mb     dM YM.    ,  MM         VVV   YM.    ,  MM           A'     VML   MM   ,AP  MM   ,AP 
// P"Ybmmd"   `Mbmmd'.JMML.        W     `Mbmmd'.JMML.       .AMA.   .AMMA. MMbmmd'   MMbmmd'  
//                                                                          MM        MM       
//                                                                        .JMML.    .JMML.    

	public void server_application(int sender, int typ, long ts, int inq)
	{	// For handling different types of messages and granting CS entry	
		ListType temp_list = new ListType();			
		switch(typ)
		{
			case 0:	//Case Emegency termination
					System.out.println("Sender:" + (sender + 1) + "\nMessage type: Emergency termination\nTimestamp:" + ts);
					break;
			case 1: 	//Case Request
					System.out.print("*******Request Case***********\n");
					System.out.println("Sender:" + (sender + 1) + "\nMessage type: Request\nTimestamp:" + ts);
					if(request_queue.isEmpty())
					{
						temp_list.setId(sender);
						temp_list.setTimestamp(ts);
						request_queue.add(temp_list);
						final String mesg_a = proc_number + "-2-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
						current_grant = sender ;
						final int send_a = sender;
						Thread thread1 = new Thread()
						{
							public void run()
							{
								obj.client(Integer.parseInt(ports.get(send_a)),hosts.get(send_a), mesg_a);
							}
						};
						thread1.start();
						System.out.println("Sending grant to "+ (current_grant+1)+ " on empty set request");
					}
					else
					{
						if( (ts<request_queue.get(0).getTimestamp()) || (ts==request_queue.get(0).getTimestamp() && sender<request_queue.get(0).getId()) )
						{
							temp_list.setId(sender);
							temp_list.setTimestamp(ts);
							final String mesg_a = proc_number + "-3-" + String.valueOf(System.currentTimeMillis() / 1000L) + "-" + sender;
							final int to_inquire = request_queue.get(0).getId();
							request_queue.add(0,temp_list);
							Thread thread1 = new Thread()
							{
								public void run()
								{
									obj.client(Integer.parseInt(ports.get(to_inquire)),hosts.get(to_inquire), mesg_a);
								}
							};
							thread1.start();
						}
						else
						{
							boolean flag = false;
							for(int i=1;i<request_queue.size();++i)
							{
								if(ts<request_queue.get(i).getTimestamp())
								{
									flag = true;
									temp_list.setId(sender);
									temp_list.setTimestamp(ts);
									final String mesg_a = proc_number + "-6-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
									request_queue.add(i,temp_list);
									final int send_b = sender;
									Thread thread1 = new Thread()
									{
										public void run()
										{
											obj.client(Integer.parseInt(ports.get(send_b)),hosts.get(send_b), mesg_a);
										}
									};
									thread1.start();
									System.out.println("Inserted in "+i +" position request queue: "+request_queue.get(i).getId());
									break;
								}
							}
							if(!flag)
							{
								System.out.println("Last in request queue: "+(sender+1));
								temp_list.setId(sender);
								temp_list.setTimestamp(ts);
								final String mesg_a = proc_number + "-6-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
								final int send_b = sender;
								request_queue.add(temp_list);
								Thread thread1 = new Thread()
								{
									public void run()
									{
										obj.client(Integer.parseInt(ports.get(send_b)),hosts.get(send_b), mesg_a);
									}
								};
								thread1.start();
								break;
							}
						}
					}
					System.out.print("\nGrant set: [ ");
					for(int x = 0;x<grant_set.size();++x)
					{
						System.out.print((grant_set.get(x)+1) + "," );
					}
					System.out.print("]");
					System.out.print("\nRequest queue: [ ");
					for(int h = 0;h<request_queue.size();++h)
					{
						System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
					}
					System.out.print("]\n");
					System.out.println("---------------------------------");
					break;
			case 2: 	//Case Grant
					System.out.print("*******Grant Case***********\n");
					if(!grant_set.contains(sender))
					{
						boolean flag = false;
						for(int i=0;i<received_grant_set.size();++i)
						{
							if(received_grant_set.get(i)==sender)
							{
								received_grant_set.remove(i);
								flag = true;
							}
						}
						if(!flag)
						{
							grant_set.add(sender);
						}
					}
					if(grant_set.size()==own_quorums.length)
					{
						server_service();
						failed_set.clear();
						grant_set.clear();
					}
					System.out.print("\nGrant set: [ ");
					for(int x = 0;x<grant_set.size();++x)
					{
						System.out.print((grant_set.get(x)+1) + "," );
					}
					System.out.print("]");
					System.out.print("\nRequest queue: [ ");
					for(int h = 0;h<request_queue.size();++h)
					{
						System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
					}
					System.out.print("]\n");
					System.out.println("Sender:" + (sender + 1) + "\nMessage type: Grant\nTimestamp:" + ts);
					System.out.println("---------------------------------");
					break;
			case 3:		//Case Inquire
					System.out.print("*******Inquire Case***********\n");
					boolean flag = false;
					if(failed_set.size()>0)
					{
						flag = true;
					}
					if(grant_set.size()<own_quorums.length)
					{
						for(int i=0;i<grant_set.size();++i)
						{
							if(grant_set.get(i) == sender)
							{
								grant_set.remove(i);
								flag = true;
								break;
							}
						}
						if(!flag)
						{
							received_grant_set.add(sender);
						}
						final String mesg_c = proc_number + "-5-" + String.valueOf(System.currentTimeMillis() / 1000L) + "-" + inq;
						final int send_c = sender;
						Thread thread1 = new Thread()
						{
							public void run()
							{
								obj.client(Integer.parseInt(ports.get(send_c)),hosts.get(send_c), mesg_c);
							}
						};
						thread1.start();
					}
					System.out.print("\nGrant set: [ ");
					for(int x = 0;x<grant_set.size();++x)
					{
						System.out.print((grant_set.get(x)+1) + "," );
					}
					System.out.print("]");
					System.out.print("\nRequest queue: [ ");
					for(int h = 0;h<request_queue.size();++h)
					{
						System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
					}
					System.out.print("]\n");
					System.out.println("Sender:" + (sender + 1) + "\nMessage type: Inquire\nTimestamp:" + ts);
					System.out.println("---------------------------------");
					break;
			case 4:		//Case Release
					System.out.print("*******Release Case***********\n");
					for(int i=0;i<request_queue.size();++i)
					{
						if(request_queue.get(i).getId()==current_grant)
						{
							request_queue.remove(i);
							break;
						}
					}
					if(request_queue.size()>0)
					{
						final String mesg_d = proc_number + "-2-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
						current_grant = request_queue.get(0).getId();
						Thread thread1 = new Thread()
						{
							public void run()
							{
								obj.client(Integer.parseInt(ports.get(current_grant)),hosts.get(current_grant), mesg_d);
							}
						};
						thread1.start();
						System.out.print("\nGrant set: [ ");
						for(int x = 0;x<grant_set.size();++x)
						{
							System.out.print((grant_set.get(x)+1) + "," );
						}
						System.out.print("]");
						System.out.print("\nRequest queue: [ ");
						for(int h = 0;h<request_queue.size();++h)
						{
							System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
						}
						System.out.print("]\n");
						System.out.println("Sending grant to "+ (current_grant+1)+ " on release");
						System.out.println("Sender:" + (sender + 1) + "\nMessage type: Release\nTimestamp:" + ts);
					}
					System.out.println("---------------------------------");
					break;
			case 5:		//Case Yeild
					System.out.print("*******Yeild Case***********\n");
					final String mesg_e = proc_number + "-2-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
					current_grant = inq;
					System.out.println("Sending grant to "+ (current_grant+1)+ " on yeild");
					Thread thread = new Thread()
					{
						public void run()
						{
							obj.client(Integer.parseInt(ports.get(current_grant)),hosts.get(current_grant), mesg_e);
						}
					};
					thread.start();
					try
					{
						Thread.sleep(100); //100ms seconds
					}
					catch(InterruptedException ex)
					{
						Thread.currentThread().interrupt();
					}
					System.out.print("\nGrant set: [ ");
					for(int x = 0;x<grant_set.size();++x)
					{
						System.out.print((grant_set.get(x)+1) + "," );
					}
					System.out.print("]");
					System.out.print("\nRequest queue: [ ");
					for(int h = 0;h<request_queue.size();++h)
					{
						System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
					}
					System.out.print("]\n");
					System.out.println("Sender:" + (sender + 1) + "\nMessage type: Yeild\nTimestamp:" + ts);
					System.out.println("---------------------------------");
					break;
			case 6:		//Case Failed
					System.out.print("*******Failed Case***********\n");
					failed_set.add(sender);
					// for(int i=0;i<grant_set.size();++i)
					// {
					// 	if(grant_set.get(i) == sender)
					// 	{
					// 		grant_set.remove(i);
					// 		break;
					// 	}
					// }
					System.out.print("\nGrant set: [ ");
					for(int x = 0;x<grant_set.size();++x)
					{
						System.out.print((grant_set.get(x)+1) + "," );
					}
					System.out.print("]");
					System.out.print("\nRequest queue: [ ");
					for(int h = 0;h<request_queue.size();++h)
					{
						System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
					}
					System.out.print("]\n");
					System.out.println("Sender:" + (sender + 1) + "\nMessage type: Failed\nTimestamp:" + ts);
					System.out.println("---------------------------------");
					break;
		}
	}


//                                         ,,                                                         
//  .M"""bgd                               db                             db                          
// ,MI    "Y                                                             ;MM:                         
// `MMb.      .gP"Ya `7Mb,od8 `7M'   `MF'`7MM  ,p6"bo   .gP"Ya          ,V^MM.   `7MMpdMAo.`7MMpdMAo. 
//   `YMMNq. ,M'   Yb  MM' "'   VA   ,V    MM 6M'  OO  ,M'   Yb        ,M  `MM     MM   `Wb  MM   `Wb 
// .     `MM 8M""""""  MM        VA ,V     MM 8M       8M""""""        AbmmmqMA    MM    M8  MM    M8 
// Mb     dM YM.    ,  MM         VVV      MM YM.    , YM.    ,       A'     VML   MM   ,AP  MM   ,AP 
// P"Ybmmd"   `Mbmmd'.JMML.        W     .JMML.YMbmd'   `Mbmmd'     .AMA.   .AMMA. MMbmmd'   MMbmmd'  
//                                                                                 MM        MM       
//                                                                               .JMML.    .JMML.     

	public void server_service()
	{
		// For executing CS enter and leave
		test_module(); 

		final String mesg_e = proc_number + "-4-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
		for(int j=0 ; j<own_quorums.length; ++j)
		{
			final int i = j;
			System.out.println("Sending release to quorum member "+own_quorums[i]);
			Thread thread1 = new Thread()
			{
				public void run()
				{
					obj.client(Integer.parseInt(ports.get(Integer.parseInt(own_quorums[i]) - 1 )),hosts.get(Integer.parseInt(own_quorums[i]) - 1 )  , mesg_e);
				}
			};
			thread1.start();
		}

	}


//                                                                       ,,               ,,          
// MMP""MM""YMM               mm                                       `7MM             `7MM          
// P'   MM   `7               MM                                         MM               MM          
//      MM  .gP"Ya  ,pP"Ybd mmMMmm     `7MMpMMMb.pMMMb.  ,pW"Wq.    ,M""bMM `7MM  `7MM    MM  .gP"Ya  
//      MM ,M'   Yb 8I   `"   MM         MM    MM    MM 6W'   `Wb ,AP    MM   MM    MM    MM ,M'   Yb 
//      MM 8M"""""" `YMMMa.   MM         MM    MM    MM 8M     M8 8MI    MM   MM    MM    MM 8M"""""" 
//      MM YM.    , L.   I8   MM         MM    MM    MM YA.   ,A9 `Mb    MM   MM    MM    MM YM.    , 
//    .JMML.`Mbmmd' M9mmmP'   `Mbmo    .JMML  JMML  JMML.`Ybmd9'   `Wbmd"MML. `Mbod"YML..JMML.`Mbmmd' 

	public void test_module()
	{
		// For checking multiple CS entries
		RandomAccessFile file = null;
		FileChannel f = null;
		FileLock lock = null;

		try
		{
			String filename = "lock.lck";
			File lockfile = new File(filename);
			file = new RandomAccessFile(lockfile, "rw");
			f = file.getChannel();
			lock = f.tryLock();
			if (lock != null)
			{
				//Enter CS here
				cs_enter();
				try
				{
					Thread.sleep(2000);				//2 seconds
				}
				catch(InterruptedException ex)
				{
					Thread.currentThread().interrupt();
				}
				//Leave CS here
				cs_leave();
				lockfile.deleteOnExit();
				ByteBuffer bytes = ByteBuffer.allocate(8);
				bytes.putLong(System.currentTimeMillis() + 10000).flip();
				f.write(bytes);
				f.force(false);
			}
			else
			{
				System.out.println("Multiple CS entry attempt by: " + (proc_number+1) );
				String filename1= "CSlog.txt";
				FileWriter fw = new FileWriter(filename1,true); //the true will append the new data
				fw.write("Multiple CS entry!! : "+(proc_number+1)); //appends the string to the file
				fw.close();
			}
		}
		catch(IOException ioe)
		{
			System.err.println("IOException: " + ioe.getMessage());
		}
		finally
		{
			try
			{
				if (lock != null && lock.isValid())
					lock.release();
				if (file != null)
					file.close();
			}
			catch(IOException ioe)
			{
				System.err.println("IOException: " + ioe.getMessage());
			}
		}	
	}


//   .g8"""bgd  .M"""bgd                         mm                   
// .dP'     `M ,MI    "Y                         MM                   
// dM'       ` `MMb.          .gP"Ya `7MMpMMMb.mmMMmm .gP"Ya `7Mb,od8 
// MM            `YMMNq.     ,M'   Yb  MM    MM  MM  ,M'   Yb  MM' "' 
// MM.         .     `MM     8M""""""  MM    MM  MM  8M""""""  MM     
// `Mb.     ,' Mb     dM     YM.    ,  MM    MM  MM  YM.    ,  MM     
//   `"bmmmd'  P"Ybmmd"       `Mbmmd'.JMML  JMML.`Mbmo`Mbmmd'.JMML.  

	public void cs_enter()
	{
		System.out.println(".----------------------------.");
		System.out.println("|Entering critical section |");
		System.out.print("\nGrant set: [ ");
		for(int x = 0;x<grant_set.size();++x)
		{
			System.out.print((grant_set.get(x)+1) + "," );
		}
		System.out.print("]");
		System.out.print("\nRequest queue: [ ");
		for(int h = 0;h<request_queue.size();++h)
		{
			System.out.print( (request_queue.get(h).getId()+1) + "-" + (request_queue.get(h).getTimestamp()) + "," );
		}
		System.out.print("]\n");
		
		try
		{
			String filename= "CSlog.txt";
			FileWriter fw = new FileWriter(filename,true); //the true will append the new data
			fw.write((proc_number+1)+ " entering critical section\n");//appends the string to the file
			fw.close();
		}
		catch(IOException ioe)
		{
			System.err.println("IOException: " + ioe.getMessage());
		}

	}


//                             ,,                                    
//   .g8"""bgd  .M"""bgd     `7MM                                    
// .dP'     `M ,MI    "Y       MM                                    
// dM'       ` `MMb.           MM  .gP"Ya   ,6"Yb.`7M'   `MF'.gP"Ya  
// MM            `YMMNq.       MM ,M'   Yb 8)   MM  VA   ,V ,M'   Yb 
// MM.         .     `MM       MM 8M""""""  ,pm9MM   VA ,V  8M"""""" 
// `Mb.     ,' Mb     dM       MM YM.    , 8M   MM    VVV   YM.    , 
//   `"bmmmd'  P"Ybmmd"      .JMML.`Mbmmd' `Moo9^Yo.   W     `Mbmmd' 

	public void cs_leave()
	{
		System.out.println("'----------------------------'");
		System.out.println("Leaving critical section");
		try
		{
			String filename= "CSlog.txt";
			FileWriter fw = new FileWriter(filename,true); //the true will append the new data
			fw.write((proc_number+1)+ " leaving critical section\n");//appends the string to the file
			fw.close();
		}
		catch(IOException ioe)
		{
			System.err.println("IOException: " + ioe.getMessage());
		}

	}


	public String byteToString(ByteBuffer byteBuffer)
	{
		//Converts byte buffer to string
		byteBuffer.position(0);
		byteBuffer.limit(MESSAGE_SIZE);
		byte[] bufArr = new byte[byteBuffer.remaining()];
		byteBuffer.get(bufArr);
		return new String(bufArr);
	}

	
	public void readconfig(String filename)
	{
		//Reads the config file and saves the hosts ports and paths in global array lists
		try
		{
			FileReader fileReader = new FileReader(filename);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			List<String> lines = new ArrayList<String>();
			String line = null;
			while ((line = bufferedReader.readLine()) != null)
			{
				lines.add(line);
			}
			bufferedReader.close();
			String[] fline = lines.toArray(new String[lines.size()]);
			nodes = Integer.parseInt(fline[0]);
			for (int i = 1; i < nodes+1; ++i)
			{
				String[] temp = fline[i].split("\t");
				hosts.add(temp[0]);
				ports.add(temp[1]);
				paths.add(temp[2]);
			}
		}
		catch(FileNotFoundException ex)
		{
			ex.printStackTrace();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
		
	}


	public static void main(String args[])
	{
		obj = new ProjCS();
		obj.readconfig(args[0]);
		final int proc_no = Integer.parseInt(args[1]) - 1;
		proc_number = proc_no; 
		
	 	String tstamp =  String.valueOf(System.currentTimeMillis() / 1000L) ;
		final String mesg = proc_no + "-1-" + tstamp;
		
		// Storing list of own quorum members
		own_quorums = paths.get(proc_no).split(" ");

		// Starting the main thread for this process
		Thread thread = new Thread()
		{
			public void run()
			{
				obj.srvr(Integer.parseInt(ports.get(proc_no)));
			}
		};
		thread.start();

		// Starting a client thread and sending the first message listed in path
		for(int j=0 ; j<own_quorums.length; ++j)
		{
			final int i = j;
			Thread thread1 = new Thread()
			{
				public void run()
				{
					obj.client(Integer.parseInt(ports.get(Integer.parseInt(own_quorums[i]) - 1 )),hosts.get(Integer.parseInt(own_quorums[i]) - 1 )  , mesg);
				}
			};
			thread1.start();
		}
	}

}