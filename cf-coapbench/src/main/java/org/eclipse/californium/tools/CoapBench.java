/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and initial implementation
 ******************************************************************************/
package org.eclipse.californium.tools;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.eclipse.californium.tools.coapbench.VirtualClientManager;


public class CoapBench {
	
	// TODO: add parameters for methods (GET, POST, ...), payload, checks, and logfile
	
	// Modes: normal, master, slave
	public static final String MASTER = "-master";
	public static final String SLAVE = "-slave";

	// Defaults
	public static final int DEFAULT_CLIENTS = 1;
	public static final int DEFAULT_SERVERS = 1;
	public static final int DEFAULT_TIME = 30; // [s]
	public static final int DEFAULT_LIFETIME = 86400;
	public static final String DEFAULT_METHOD = "GET";

	public static final String DEFAULT_MASTER_ADDRESS = "localhost";
	public static final int DEFAULT_MASTER_PORT = 58888; 
	
	public static void main(String[] args) {
		try {
			if (args.length > 0) {
				if ("-usage".equals(args[0]) || "-help".equals(args[0]) || "-?".equals(args[0])) {
					printUsage();
				} else if (args[0].equals(MASTER)) {
					mainMaster(args);
				} else if (args[0].equals(SLAVE)) {
					mainSlave(args);
				} else {
					mainBench(args);
				}
			} else {
				printUsage();
			}
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(-1);
		}
	}
	
	public static void mainBench(String[] args) throws Exception {
		String target = null;
		String bindAddr = null;
		String bindMultipleAddr = null;
		boolean multipleAddr = false;
		String payload = null;
		String method = DEFAULT_METHOD;
		String clients = ""+DEFAULT_CLIENTS;
		String scheme = null;
		int time = DEFAULT_TIME;
		int index = 0;
		boolean withLatency = false;
		String register = null;
		int timeout = 0;
		while (index < args.length) {
			String arg = args[index];
			
			if ("-c".equals(arg)) {
				clients = args[index+1];
			} else if ("-t".equals(arg)) {
				time = Integer.parseInt(args[index+1]);
			} else if ("-b".equals(arg)) {
				bindAddr = args[index+1];
			} else if ("-d".equals(arg)) {
				bindMultipleAddr = args[index+1];
			} else if ("-m".equals(arg)) {
				method = args[index+1];
			} else if ("-R".equals(arg)) {
				register = args[index+1];
			} else if ("-lt".equals(arg)) {
				timeout = Integer.parseInt(args[index+1]);
			} else if ("-y".equals(arg)) {
				payload = readPayload(args[index+1]);
			} else if ("-latency".equals(arg)) {
				withLatency = true; index++; continue;
			} else if ("-h".equals(arg)) {
				printUsage();
				return;
			} else if (index == args.length - 1) {
				// The last argument is the target address
				target = arg;
			} else {
				System.err.println("Unknwon arg "+arg);
				printUsage();
				return;
			}
			index += 2;
		}
		if (target == null){
			System.err.println("Error: No target specified");
			printUsage();
			return;
		}

		scheme = target;

		/* We have to create a registration URI as follow: POST coap://address:port/?lt=time&ep=name*/
		if (register != null){
			
			if (!target.substring(target.length() - 1).equals("/")){
				target +=  '/';
			}
			// The registration must be a POST message 
			method = "POST";
			
			/* Set up the lifetime variable correctly */
			if (timeout > DEFAULT_LIFETIME) {
				timeout = DEFAULT_LIFETIME; 
			} else if (timeout < 60) {
				if (time >60){
					timeout = time;
				} else {
					timeout = 60;
				}
			}
			
			//Add the lt variable into the URI
			//wakaama needs the definition of lwm2m=1.0, even though it is not in the standards
			//String lt = "rd?lwm2m=1.0&lt=" + timeout ;
			String lt = "rd?lt=" + timeout ;
			target +=  lt;
			//Add the ep variable into the URI
			String ep = "&ep=" + register;
			target +=  ep;
		}
			
		//URI uri = new URI(target);
		
		InetSocketAddress bindSAddr = null;
		if (bindMultipleAddr != null) {
			InetAddress ba = InetAddress.getByName(bindMultipleAddr);
			bindSAddr = new InetSocketAddress(ba, 0);
			multipleAddr = true;
			System.err.println("Bind clients to multiple IP addresses starting from: "+bindSAddr);
			System.err.println("Note that the binding does not check the available IPs in the system. The user should check that before assigning the IPs.");
		} else if (bindAddr != null) {
			InetAddress ba = InetAddress.getByName(bindAddr);
			bindSAddr = new InetSocketAddress(ba, 0);
			System.err.println("Bind clients to local address: "+bindSAddr);
			System.err.println("Note that on some systems (e.g. Windows) it now is not possible to send requests to localhost.");
		}

		int[] series = convertSeries(clients);
		VirtualClientManager manager = new VirtualClientManager(target, bindSAddr, method, payload, multipleAddr);
		manager.setRegistration(register!=null);
        manager.setScheme(scheme);
		if (withLatency) manager.setEnableLatency(true);
		manager.runConcurrencySeries(series, time*1000);
		
//		Thread.sleep(time*1000 + 1000);
		System.exit(0); // stop all threads from virtual client manager
	}
	
	public static void mainMaster(String[] args) throws Exception {
		int port = DEFAULT_MASTER_PORT;
		int index = 1;
		while (index < args.length) {
			String arg = args[index];
			if ("-p".equals(arg)) {
				port = Integer.parseInt(args[index+1]);
			} else {
				System.err.println("Unknwon arg "+arg);
				printUsage();
				return;
			}
			index += 2;
		}
		new ClientMaster(port).start();
	}
	
	public static void mainSlave(String[] args) throws Exception {
		String address = DEFAULT_MASTER_ADDRESS;
		int port = DEFAULT_MASTER_PORT;
		int index = 1;
		boolean verbose = false;
		//String requestType;
		while (index < args.length) {
			String arg = args[index];
			if ("-a".equals(arg)) {
				address = args[index+1];
			} else if ("-p".equals(arg)) {
				port = Integer.parseInt(args[index+1]);
			} else if ("-v".equals(arg)) {
				verbose = true;
			//} else if ("-m".equals(arg)) {
			//	requestType = args[index+1];
			}
			index += 2;
		}

		ClientSlave slave = new ClientSlave(InetAddress.getByName(address), port);
		slave.setVerbose(verbose);
		slave.start();
	}
	
//	private static int[] convertSeries(String clientSeries) {
//		// clientSeries is in format <from>:<step>:<to>
//		int from = 0;
//		int to = 0;
//		int step = 1;
//		if (clientSeries == null)
//			return new int[] { DEFAULT_CLIENTS };
//		
//		else if (clientSeries.matches("\\d+"))
//			return new int[] { Integer.parseInt(clientSeries) };
//		
//		else if (clientSeries.matches("\\d+:\\d+")) {
//			from = Integer.parseInt(clientSeries.split(":")[0]);
//			to =   Integer.parseInt(clientSeries.split(":")[1]);
//		
//		} else if (clientSeries.matches("\\d+:\\d+:\\d+")) {
//			from = Integer.parseInt(clientSeries.split(":")[0]);
//			step = Integer.parseInt(clientSeries.split(":")[1]);
//			to =   Integer.parseInt(clientSeries.split(":")[2]);
//		}
//		int length = (to-from)/step + 1;
//		int[] series = new int[length];
//		for (int i=0;i<length;i++)
//			series[i] = from + i*step;
//		return series;
//	}
	
	private static int[] convertSeries(String clientSeries) {
		// clientSeries is in format first,second,third...
		String[] parts = clientSeries.split(",");
		int[] series = new int[parts.length];
		for (int i=0;i<parts.length;i++)
			series[i] = Integer.parseInt(parts[i]);
		return series;
	}
	
	private static String readPayload(String file) {
		String payload = null;
		try(BufferedReader br = new BufferedReader(new FileReader(file))) {
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();

		    while (line != null) {
		        sb.append(line);
		        //TODO: Comment this line for wakamaa
		        //sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
		    payload = sb.toString();
		} catch (FileNotFoundException e) {
			System.out.println("File of the payload cannot be found: " + file);
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return payload;
	}
	
	public static void printUsage() {
		System.out.println(
				"SYNOPSIS"
				+ "\n    CoAPBench [[OPTIONS] URI | -master OPTIONS | -slave OPTIONS] [-v]" 
				+ "\n"
				+ "\nURI: The target URI to benchmark"
				+ "\n"
				+ "\nOPTIONS are:"
				+ "\n    -c CONCURRENCY"
				+ "\n            Concurrency level, i.e., the number of parallel clients (default is "+ DEFAULT_CLIENTS + ")."
				+ "\n            This value can be of the form <from>:<step>:<to>, e.g., 10:2:16 for a subsequent run of 10, 12, 14, 16 clients."
				+ "\n    -t TIME"
				+ "\n            Limit the duration of the benchmark to TIME seconds (default is " + DEFAULT_TIME + ")."
				+ "\n    -m METHOD"
				+ "\n            Defines the method of the operation. The values can be GET, POST, PUT and DELETE (default is " + DEFAULT_METHOD + ")."
				+ "\n    -y File"
				+ "\n            This option expects a filename and specifies the payload of the operation. The file has to be a text file."
				+ "\n    -b ADDRESS"
				+ "\n            Bind the clients to the specified local address (by default the system chooses)."
				+ "\n    -d ADDRESS"
				+ "\n            Bind every client to a different IP address starting from the specified ADDRESS. Remember to add first the address to your local interface."
				+ "\nLWM2M operations:"
				+ "\n    -R name [-lt integer]"
				+ "\n            Register the clients to the specified local address. Every client is identified by an integer from 1 to the maximum"
				+ "CONCURRENCY value. The tool uses that information to register every client with a different endpoint name adding a variable:"
				+ "\"ep=name+CONCURRENCY number\" at the end of the URI." 
				+ "\n"
				+ "\n            \"lt\" indicates the expected lifetime of a Registration in seconds, maximum lifetime is 86400 seconds and minimum is 60. If \"lt\" is not "
				+ "specified, the time in the \"t\" parameter is used. The tool adds a new variable \"lt=integer\" at the end of the URI"
				+ "\n"
				+ "\nOPTIONS for the master are:"
				+ "\n    -p PORT"
				+ "\n            The port on which the master waits for slaves."
				+ "\n"
				+ "\nOPTIONS for the slave are:"
				+ "\n    -a ADDRESS"
				+ "\n            The address of the master."
				+ "\n    -p PORT"
				+ "\n            The port of the master."
				+ "\n    -s"
				+ "\n            Specifies whether the resource should be observed (applies if the request type is set to GET)."
				+ "\n"
				+ "\nExamples:"
				+ "\nStart 50 clients that concurrently send GET requests for 60 seconds"
				+ "\n    java -jar coapbench.jar -c 50 -t 60 coap://localhost:5683/benchmark"
				+ "\n"
				+ "\nStart a master listening on port 8888 for slaves"
				+ "\n    java -jar coapbench.jar -master -p 8888"
				+ "\n"
				+ "\nStart a slave which connects with the specified master"
				+ "\n    java -jar coapbench.jar -slave -a 192.168.1.33 -p 8888"
				+ "\n"
				+ "\nLWM2M examples:"
				+ "\n"
				+ "\nRegister 50 clients with endpoints names ranging from \"node1\" to \"node50\" for 60 seconds. After that, the clients"
				+ "send updates for that registration. The clients get automatically de-register after 70 seconds."
				+ "\n    java -jar coapbench.jar -c 50 -y file.txt -t 60 -R node -lt 70 coap://localhost:5683/"
				+ "\n"
			);
		// TODO: add parameters for methods (GET, POST, ...), payload, checks, and logfile
		// TODO: stepwise increase
	}
	
}
