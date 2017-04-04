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
package org.eclipse.californium.tools.coapbench;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The VirtualClient manager creates the virtual clients for the benchmarks.
 * Each virtual client sends request to the server as fast as the server can
 * handle them.
 */
public class VirtualClientManager {

	public static final String LOG_FILE = "coapbench";
	
	private Timer timer;

	private URI uri;
	private String target; 
	private InetSocketAddress bindAddr;
	private String method;
	private String payload;
	private boolean register = false;
	private String scheme;

	private long timestamp;
	private int count;
	private ArrayList<VirtualClient> clients;
	
	private LogFile log;
	
	private boolean enableLatency = false;
	private boolean verbose;

	public VirtualClientManager() throws Exception {
		this(null);
	}
	
	public VirtualClientManager(String target) throws Exception {
		this(target, null, null, null);
	}
	
	public VirtualClientManager(String target, InetSocketAddress bindAddr, String method, String payload) throws Exception {
		this.target = target;
		this.bindAddr = bindAddr;
		this.method = method;
		this.payload = payload;
		this.clients = new ArrayList<VirtualClient>();
		this.timer = new Timer();
	}
	
	public void runConcurrencySeries(int[] cs, int time) throws Exception {
		int n = cs.length;
		log("Run series: "+Arrays.toString(cs).replace("[","").replace("]", ""));
		
		for (int i=0;i<n;i++) {
			start(cs[i], time);
			
			if (i < n-1) // sleep between two runs
				Thread.sleep(time + 5*1000);
			else Thread.sleep(time + 1000);
		}
	}
	
	public void log(String entry) throws Exception {
		ensurelog();
		log.println(entry);
	}
	
	public void lognew(String name) throws Exception {
		this.log = new LogFile(LOG_FILE + "_" + name);
		this.log.setVerbose(verbose);
	}
	
	private void ensurelog() throws Exception {
		if (log==null) {
			log = new LogFile(LOG_FILE);
			log.format("Timeouts, Concurrency, Time, Completed, Throughput | 0%%, 10%%, 20%%, 30%%, 40%%, 50%%, 60%%, 65%%, 70%%, 75%%, 80%%, 90%%, 95%%, 98%%, 99%%, 100%%, stdev(ms)\n");
		}
	}
	
	public void setClientCount(int c) throws Exception {
		if (c < clients.size()) {
			for (int i=clients.size()-1; i>=c; i--)
				clients.remove(i).close(); // close and remove
		} else {
			if (!register){ 
				uri = new URI(target);
				for (int i=clients.size(); i<c; i++) {
					VirtualClient vc = new VirtualClient(uri, bindAddr, method, payload);
					vc.setCheckLatency(enableLatency);
					clients.add(vc);
				}
			} else {
				for (int i=clients.size(); i<c; i++) {
					uri = new URI(target + Integer.toString(i));
					VirtualClient vc = new VirtualClient(uri, bindAddr, method, payload);
					vc.setRegistration(true);
					vc.setScheme(scheme);
					vc.setCheckLatency(enableLatency);
					clients.add(vc);
				}
			}
		}
		this.count = c;
	}
	
	public void setURI(URI uri) throws UnknownHostException {
		this.uri = uri;
		for (VirtualClient vc:clients)
			vc.setURI(uri, method, payload);
	}
	
	public void start(int count, int time) throws Exception {
		ensurelog();
		setClientCount(count);
		Thread[] threads = new Thread[count];
		for (int i=0;i<count;i++) {
			VirtualClient c = clients.get(i);
			c.reset();
			threads[i] = new Thread(c);
		}
		System.err.println("\nStart "+count+" virtual clients for "+time+" ms");
		for (int i=0;i<count;i++)
			threads[i].start();
		timestamp = System.nanoTime();
		timer.schedule(new TimerTask() {
			public void run() {
				stop();
			} }, time);
	}
	
	public static void write (String filename, int[]x) throws IOException{
		  BufferedWriter outputWriter = null;
		  outputWriter = new BufferedWriter(new FileWriter(filename));
		  for (int i = 0; i < x.length; i++) {
		    // Maybe:
		    outputWriter.write(x[i]+"");
		    // Or:
		    //outputWriter.write(Integer.toString(x[i]);
		    outputWriter.newLine();
		  }
		  outputWriter.flush();  
		  outputWriter.close();  
		}
	
	public synchronized void stop() {
		float dt = (System.nanoTime() - timestamp) / 1000000f;
		if (verbose)
			System.out.println("Stop virtual clients and collect results");
		for (VirtualClient vc:clients)
			vc.stop();
		int sum = 0;
		int sumTimeout = 0;
		IntArray latencies = new IntArray();
		for (int i=0;i<clients.size();i++) {
			VirtualClient client = clients.get(i);
			int count = client.getCount();
			int lost = client.getTimeouts();
			for (int lat : client.getLatencies()) {
				latencies.add(lat);
			}
			sum += count;
			sumTimeout += lost;
			if (verbose)
				System.out.format("Virtual client %2d received %7d, timeout %3d, throughput %d /s\n"
					, i, count, lost, (int) (count * 1000L / dt));
		}
		float throughput = (sum * 1000L) / dt;
		
		int[] lats = latencies.getArray();
		long latsum = 0;
		for (int l:lats) latsum += l;
		double mean = (double) latsum / lats.length;
		double temp = 0;
        for(int l :lats) temp += (mean-l)*(mean-l);
        double var = Math.sqrt(temp / lats.length);
           
        if (lats.length > 0) {
        	Arrays.sort(lats); // bad if length==0
        	//write("log_array", lats);
        	int q00 = lats[(int) (lats.length * 0L/100)];
        	int q10 = lats[(int) (lats.length * 10L/100)];
        	int q20 = lats[(int) (lats.length * 20L/100)];
        	int q30 = lats[(int) (lats.length * 30L/100)];
        	int q40 = lats[(int) (lats.length * 40L/100)];
			int q50 = lats[(int) (lats.length/2)];
			int q60 = lats[(int) (lats.length * 60L/100)];
			int q66 = lats[(int) (lats.length * 2L/3)];
			int q70 = lats[(int) (lats.length * 70L/100)];
			int q75 = lats[(int) (lats.length * 3L/4)];
			int q80 = lats[(int) (lats.length * 4L/5)];
			int q90 = lats[(int) (lats.length * 9L/10)];
			int q95 = lats[(int) (lats.length * 19L/20)];
			int q98 = lats[(int) (lats.length * 98L/100)];
			int q99 = lats[(int) (lats.length * 99L/100)];
			int q100 = lats[lats.length - 1];
			
			/*log.format("%d, %d, %.3f, %d, %.2f | %d, %d, %d, %d, %d, %d, %d, %d, %d, %.1f\n",
					sumTimeout, count, dt/1000f, sum, throughput,
					q50, q66, q75, q80, q90, q95, q98, q99, q100, var);*/
			log.format("%d, %d, %.3f, %d, %.2f | %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %d, %.1f\n",
					sumTimeout, count, dt/1000f, sum, throughput,
					q00, q10, q20, q30, q40, q50, q60, q66, q70, q75, q80, q90, q95, q98, q99, q100, var);
        } else {
        	// no latency
        	log.format("c=%d, t=%.3f, received=%d, timeouts=%d, throughput=%.2f, uri=%s\n", count, dt/1000f, sum, sumTimeout, throughput, uri.toString());
        }
	}

	public boolean isEnableLatency() {
		return enableLatency;
	}

	public void setEnableLatency(boolean enableLatency) {
		System.err.println("Measure latency: "+enableLatency);
		this.enableLatency = enableLatency;
	}

	public void setRegistration(boolean registration) {
		this.register = registration;
	}

	public void setScheme(String scheme){
		this.scheme = scheme;
	}
	
	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
		if (log != null)
			log.setVerbose(verbose);
	}
}

