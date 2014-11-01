import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import com.sun.nio.sctp.*;
import java.nio.*;

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
	static ArrayList<ListType> uncertain_list = new ArrayList<ListType>();
	static ArrayList<Integer> grant_set = new ArrayList<Integer>();
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
	
	EMERGENCY TERMINATION: -1

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
				server_application(sender,typ,ts);
				// Call Application function here
			}
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}

	// Placeholder for application 
	public void server_application(int sender, int typ, long ts)
	{
		ListType temp_list = new ListType();			
		switch(typ)
		{
			case 1: 	//Case Request
					System.out.println("Sender:" + sender + "\nMessage type: Request\nTimestamp:" + ts);
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
					}
					else
					{
						if( (ts<request_queue.get(0).getTimestamp()) || (ts==request_queue.get(0).getTimestamp() && sender<request_queue.get(0).getId()) )
						{
							temp_list.setId(sender);
							temp_list.setTimestamp(ts);
							final String mesg_a = proc_number + "-3-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
							Thread thread1 = new Thread()
							{
								public void run()
								{
									obj.client(Integer.parseInt(ports.get(request_queue.get(0).getId())),hosts.get(request_queue.get(0).getId()), mesg_a);
								}
							};
							thread1.start();
							request_queue.add(0,temp_list);
							for(int j=0; j<uncertain_list.size();++j)
							{
								if(ts<uncertain_list.get(j).getTimestamp())
								{
									uncertain_list.add(j,temp_list);
									break;
								}
								else if(j==(uncertain_list.size()-1))
								{
									uncertain_list.add(temp_list);
								}
							}
						}
						else
						{
							for(int i=1;i<request_queue.size();++i)
							{
								if(ts<request_queue.get(i).getTimestamp())
								{
									temp_list.setId(sender);
									temp_list.setTimestamp(ts);
									request_queue.add(i,temp_list);
									final String mesg_a = proc_number + "-6-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
									final int send_b = sender;
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
								else if(i==(request_queue.size()-1))
								{
									temp_list.setId(sender);
									temp_list.setTimestamp(ts);
									request_queue.add(temp_list);
									final String mesg_a = proc_number + "-6-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
									final int send_b = sender;
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
					}
					break;
			case 2: 	//Case Grant
					grant_set.add(sender);
					if(grant_set.size()==own_quorums.length)
					{
						server_service();
					}
					System.out.println("Sender:" + sender + "\nMessage type: Grant\nTimestamp:" + ts);
					break;
			case 3:		//Case Inquire
					if(failed_set.size()>0)
					{
						final String mesg_c = proc_number + "-5-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
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
					else
					{
						System.out.println("Received rogue inquire message. Inconsistent system state.");
					}
					System.out.println("Sender:" + sender + "\nMessage type: Inquire\nTimestamp:" + ts);
					break;
			case 4:		//Case Release
					for(int i=0;i<request_queue.size();++i)
					{
						if(request_queue.get(i).getId()==current_grant)
						{
							request_queue.remove(i);
							break;
						}
					}
					final String mesg_d = proc_number + "-2-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
					current_grant = request_queue.get(0).getId();
					Thread thread1 = new Thread()
					{
						public void run()
						{
							obj.client(Integer.parseInt(ports.get(request_queue.get(0).getId())),hosts.get(request_queue.get(0).getId()), mesg_d);
						}
					};
					thread1.start();
					System.out.println("Sender:" + sender + "\nMessage type: Release\nTimestamp:" + ts);
					break;
			case 5:		//Case Yeild
					final String mesg_e = proc_number + "-2-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
					current_grant = uncertain_list.get(0).getId() ;
					Thread thread = new Thread()
					{
						public void run()
						{
							obj.client(Integer.parseInt(ports.get(uncertain_list.get(0).getId())),hosts.get(uncertain_list.get(0).getId()), mesg_e);
						}
					};
					thread.start();
					uncertain_list.remove(0);
					for(int i=0;i<uncertain_list.size();++i )
					{
						final int j = i;
						final String mesg_e1 = proc_number + "-6-" + String.valueOf(System.currentTimeMillis() / 1000L) ;
						Thread thread2 = new Thread()
						{
							public void run()
							{
								obj.client(Integer.parseInt(ports.get(uncertain_list.get(j).getId())),hosts.get(uncertain_list.get(j).getId()), mesg_e1);
							}
						};
						thread2.start();
					}
					uncertain_list.clear();
					System.out.println("Sender:" + sender + "\nMessage type: Yeild\nTimestamp:" + ts);
					break;
			case 6:		//Case Failed
					failed_set.add(sender);
					System.out.println("Sender:" + sender + "\nMessage type: Emergency termination\nTimestamp:" + ts);
					break;
			case -1:	//Case Emegency termination
					System.out.println("Sender:" + sender + "\nMessage type: Request\nTimestamp:" + ts);
					break;
		}
	}

	public void server_service()
	{
		cs_enter();
		cs_leave();
	}

	public void cs_enter()
	{
		System.out.println("Entering critical section");

	}

	public void cs_leave()
	{
		System.out.println("Exiting critical section");

	}

	//Converts byte buffer to string
	public String byteToString(ByteBuffer byteBuffer)
	{
		byteBuffer.position(0);
		byteBuffer.limit(MESSAGE_SIZE);
		byte[] bufArr = new byte[byteBuffer.remaining()];
		byteBuffer.get(bufArr);
		return new String(bufArr);
	}

	//Reads the config file and saves the hosts ports and paths in global array lists
	public void readconfig(String filename)
	{
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
		final String first_send_port = ports.get(Integer.parseInt(paths.get(proc_no).split(" ")[0]) - 1) ;
		final String first_send_host = hosts.get(Integer.parseInt(paths.get(proc_no).split(" ")[0]) - 1) ;
		
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
			// System.out.println(own_quorums[i]);
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